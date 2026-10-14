import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { Router, RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged, of, switchMap } from 'rxjs';

import { UnsavedForm } from '../../core/guards/unsaved-changes.guard';
import { ProductRecord } from '../../core/models/master.model';
import { NotificationService } from '../../core/services/notification.service';
import { readApiError } from '../../core/utils/http-error.util';
import { PageHeader } from '../../shared/components/page-header/page-header';
import { InventoryApi } from '../inventory/inventory-api.service';
import { formatQty, today } from '../inventory/inventory-format';
import { InventoryNav } from '../inventory/inventory-nav';
import { StockLocation } from '../inventory/inventory.model';
import { ProductApi } from '../products/product-api.service';

@Component({
  selector: 'app-adjustment-form-page',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    RouterLink,
    PageHeader,
    InventoryNav,
  ],
  templateUrl: './adjustment-form-page.html',
  styleUrls: ['../inventory/inventory-screen.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdjustmentFormPage implements UnsavedForm {
  private readonly api = inject(InventoryApi);
  private readonly products = inject(ProductApi);
  private readonly router = inject(Router);
  private refreshStock(): void {
    const product = this.product();
    if (!product) {
      this.onHand.set(null);
      return;
    }
    const location = this.form.controls.locationId.value;
    this.api.product(product.id, location ? Number(location) : null).subscribe({
      next: (stock) => this.onHand.set(Number(stock.position.currentStock)),
      error: () => this.onHand.set(null),
    });
  }

  private readonly notify = inject(NotificationService);
  private readonly formBuilder = inject(FormBuilder);

  readonly productSearch = new FormControl('', { nonNullable: true });
  readonly form = this.formBuilder.nonNullable.group({
    locationId: [''],
    direction: ['INCREASE', Validators.required],
    quantity: ['1', Validators.required],
    unitCost: [''],
    reason: ['COUNT_CORRECTION', Validators.required],
    referenceNumber: [''],
    adjustmentDate: [today(), Validators.required],
    notes: [''],
    batch: [''],
    expiryDate: [''],
    serials: [''],
  });

  readonly qty = formatQty;
  readonly locations = signal<StockLocation[]>([]);
  readonly hits = signal<ProductRecord[]>([]);
  readonly product = signal<ProductRecord | null>(null);
  readonly onHand = signal<number | null>(null);
  private readonly previewTick = signal(0);
  readonly saving = signal(false);
  readonly saved = signal(false);
  readonly errorMessage = signal('');

  constructor() {
    this.api.locations().subscribe({
      next: (locations) => this.locations.set(locations),
      error: () => this.locations.set([]),
    });
    this.productSearch.valueChanges
      .pipe(
        debounceTime(200),
        distinctUntilChanged(),
        switchMap((term) =>
          term.trim().length < 1 ? of({ items: [] as ProductRecord[] }) : this.products.list(term.trim(), 'ACTIVE', '', null, 0, 'name,asc', 8),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((page) => this.hits.set(page.items.filter((item) => item.stockType !== 'NONE')));
    this.form.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.previewTick.update((value) => value + 1));
    this.form.controls.locationId.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.refreshStock());
    this.form.controls.direction.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
      const allowed = this.reasons();
      if (!allowed.includes(this.form.controls.reason.value)) {
        this.form.controls.reason.setValue(allowed[0]);
      }
    });
  }

  reasons(): string[] {
    if (this.form.controls.direction.value === 'DECREASE') {
      return ['DAMAGE', 'THEFT', 'COUNT_CORRECTION', 'WRITE_OFF', 'OTHER'];
    }
    return ['OPENING_STOCK', 'COUNT_CORRECTION', 'FOUND', 'OTHER'];
  }

  hasUnsavedChanges(): boolean {
    return !this.saved() && (this.product() != null || this.form.dirty);
  }

  choose(product: ProductRecord): void {
    this.product.set(product);
    this.productSearch.setValue(product.name, { emitEvent: false });
    this.hits.set([]);
    this.refreshStock();
  }

  resulting(): number | null {
    this.previewTick();
    const current = this.onHand();
    if (current == null) {
      return null;
    }
    const quantity = Number(this.form.controls.quantity.value);
    if (!Number.isFinite(quantity)) {
      return null;
    }
    return this.form.controls.direction.value === 'DECREASE' ? current - quantity : current + quantity;
  }

  belowZero(): boolean {
    const next = this.resulting();
    return next != null && next < 0;
  }

  save(): void {
    const product = this.product();
    if (!product || this.form.invalid) {
      this.errorMessage.set('Choose a stocked product and complete the required fields.');
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    const serials = value.serials
      .split(/[\n,]/)
      .map((serial) => serial.trim())
      .filter((serial) => serial.length > 0);
    this.saving.set(true);
    this.errorMessage.set('');
    this.api
      .createAdjustment({
        productId: product.id,
        locationId: value.locationId ? Number(value.locationId) : null,
        batch: value.batch.trim() || null,
        expiryDate: value.expiryDate || null,
        direction: value.direction,
        quantity: value.quantity,
        unitCost: value.unitCost.trim() || null,
        reason: value.reason,
        referenceNumber: value.referenceNumber.trim() || null,
        adjustmentDate: value.adjustmentDate,
        notes: value.notes.trim() || null,
        serials: serials.length > 0 ? serials : null,
      })
      .subscribe({
        next: (adjustment) => {
          this.saved.set(true);
          this.notify.success(`${adjustment.adjustmentNumber} posted`);
          void this.router.navigate(['/stock-adjustment']);
        },
        error: (error: unknown) => {
          this.saving.set(false);
          this.errorMessage.set(readApiError(error));
        },
      });
  }
}

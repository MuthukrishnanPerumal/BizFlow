import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormArray, FormBuilder, FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
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
import { today } from '../inventory/inventory-format';
import { InventoryNav } from '../inventory/inventory-nav';
import { StockLocation } from '../inventory/inventory.model';
import { ProductApi } from '../products/product-api.service';

@Component({
  selector: 'app-transfer-form-page',
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
  templateUrl: './transfer-form-page.html',
  styleUrls: ['../inventory/inventory-screen.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TransferFormPage implements UnsavedForm {
  private readonly api = inject(InventoryApi);
  private readonly products = inject(ProductApi);
  private readonly router = inject(Router);
  private readonly notify = inject(NotificationService);
  private readonly formBuilder = inject(FormBuilder);

  readonly productSearch = new FormControl('', { nonNullable: true });
  readonly locationCode = new FormControl('', { nonNullable: true });
  readonly locationName = new FormControl('', { nonNullable: true });
  readonly form = this.formBuilder.group({
    fromLocationId: this.formBuilder.control<number | null>(null, Validators.required),
    toLocationId: this.formBuilder.control<number | null>(null, Validators.required),
    transferDate: [today(), Validators.required],
    reason: ['', Validators.required],
    notes: [''],
    lines: this.formBuilder.array<ReturnType<TransferFormPage['lineGroup']>>([]),
  });

  readonly locations = signal<StockLocation[]>([]);
  readonly hits = signal<ProductRecord[]>([]);
  readonly saving = signal(false);
  readonly saved = signal(false);
  readonly errorMessage = signal('');

  constructor() {
    this.loadLocations(null);
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
  }

  get lines(): FormArray<ReturnType<TransferFormPage['lineGroup']>> {
    return this.form.controls.lines;
  }

  hasUnsavedChanges(): boolean {
    return !this.saved() && (this.lines.length > 0 || this.form.dirty);
  }

  addProduct(product: ProductRecord): void {
    this.lines.push(this.lineGroup(product));
    this.productSearch.setValue('', { emitEvent: false });
    this.hits.set([]);
  }

  removeLine(index: number): void {
    this.lines.removeAt(index);
  }

  createLocation(): void {
    const code = this.locationCode.value.trim();
    const name = this.locationName.value.trim();
    if (!code || !name) {
      this.errorMessage.set('Enter a location code and name.');
      return;
    }
    this.api.createLocation(code, name).subscribe({
      next: (location) => {
        this.locationCode.setValue('');
        this.locationName.setValue('');
        this.notify.success(`${location.name} is ready`);
        this.loadLocations(location.id);
      },
      error: (error: unknown) => this.errorMessage.set(readApiError(error)),
    });
  }

  save(complete: boolean): void {
    if (this.form.invalid || this.lines.length === 0) {
      this.form.markAllAsTouched();
      this.errorMessage.set('Choose two locations, a reason, and at least one product.');
      return;
    }
    const value = this.form.getRawValue();
    if (value.fromLocationId == null || value.toLocationId == null) {
      this.errorMessage.set('Choose a source and a destination location.');
      return;
    }
    if (value.fromLocationId === value.toLocationId) {
      this.errorMessage.set('Choose a different destination location.');
      return;
    }
    this.saving.set(true);
    this.errorMessage.set('');
    this.api
      .createTransfer({
        fromLocationId: value.fromLocationId,
        toLocationId: value.toLocationId,
        transferDate: value.transferDate ?? today(),
        reason: (value.reason ?? '').trim(),
        notes: (value.notes ?? '').trim() || null,
        complete,
        lines: value.lines.map((line) => ({
          productId: line.productId,
          batch: line.batch.trim() || null,
          expiryDate: line.expiryDate || null,
          quantity: line.quantity,
          serials: splitSerials(line.serials),
        })),
      })
      .subscribe({
        next: (transfer) => {
          this.saved.set(true);
          this.notify.success(`${transfer.transferNumber} ${complete ? 'completed' : 'saved'}`);
          void this.router.navigate(['/stock-transfer', transfer.id]);
        },
        error: (error: unknown) => {
          this.saving.set(false);
          this.errorMessage.set(readApiError(error));
        },
      });
  }

  private lineGroup(product: ProductRecord) {
    return this.formBuilder.nonNullable.group({
      productId: product.id,
      label: `${product.name} · ${product.productCode}`,
      batch: '',
      expiryDate: '',
      quantity: '1',
      serials: '',
      batchRequired: product.batchTracking || product.stockType === 'BATCH',
      expiryRequired: product.expiryTracking,
      serialRequired: product.serialTracking,
    });
  }

  private loadLocations(selectId: number | null): void {
    this.api.locations().subscribe({
      next: (locations) => {
        this.locations.set(locations);
        const from = locations.find((location) => location.defaultLocation) ?? locations[0];
        if (from && this.form.controls.fromLocationId.value == null) {
          this.form.controls.fromLocationId.setValue(from.id);
        }
        if (selectId != null) {
          this.form.controls.toLocationId.setValue(selectId);
        }
      },
      error: () => this.locations.set([]),
    });
  }
}

function splitSerials(value: string): string[] | null {
  const serials = value
    .split(/[\n,]/)
    .map((serial) => serial.trim())
    .filter((serial) => serial.length > 0);
  return serials.length > 0 ? serials : null;
}

import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, HostListener, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';
import { UnsavedForm } from '../../core/guards/unsaved-changes.guard';
import { MasterRef, ProductRecord, RecordStatus, StockType } from '../../core/models/master.model';
import { ConfirmationService } from '../../core/services/confirmation.service';
import { NotificationService } from '../../core/services/notification.service';
import { readApiError, readFieldErrors } from '../../core/utils/http-error.util';
import { InventoryApi } from '../inventory/inventory-api.service';
import { ProductStock } from '../inventory/inventory.model';
import { RecordLookup } from '../master/record-lookup';
import { blankToNull, formatMoney } from '../master/master-format';
import { formatQty } from '../reporting/reporting-format';
import { EmptyState } from '../../shared/components/empty-state/empty-state';
import { ErrorState } from '../../shared/components/error-state/error-state';
import { FormSection } from '../../shared/components/form-section/form-section';
import { PageHeader } from '../../shared/components/page-header/page-header';
import { SkeletonLoader } from '../../shared/components/skeleton-loader/skeleton-loader';
import { StatusBadge } from '../../shared/components/status-badge/status-badge';
import { ProductApi, ProductWrite } from './product-api.service';
import { productFieldErrors, trackingFor } from './product-rules';

const MONEY = /^\d+(\.\d{1,4})?$/;

@Component({
  selector: 'app-product-form-page',
  imports: [
    NgTemplateOutlet,
    ReactiveFormsModule,
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    RouterLink,
    PageHeader,
    FormSection,
    RecordLookup,
    StatusBadge,
    SkeletonLoader,
    EmptyState,
    ErrorState,
  ],
  templateUrl: './product-form-page.html',
  styleUrls: ['../master/master-form.scss', '../../shared/components/data-table/data-table.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProductFormPage implements UnsavedForm {
  private readonly formBuilder = inject(FormBuilder);
  private readonly api = inject(ProductApi);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly notifications = inject(NotificationService);
  private readonly confirmation = inject(ConfirmationService);
  private readonly inventory = inject(InventoryApi);
  readonly auth = inject(AuthService);
  readonly money = formatMoney;
  readonly qty = formatQty;

  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly tab = signal<'overview' | 'inventory' | 'edit'>('overview');
  readonly advancedOpen = signal(false);
  readonly record = signal<ProductRecord | null>(null);
  readonly stock = signal<ProductStock | null>(null);
  readonly stockLoading = signal(false);
  readonly stockFailed = signal(false);
  readonly productId = signal<number | null>(null);
  readonly version = signal<number | null>(null);
  readonly category = signal<MasterRef | null>(null);
  readonly brand = signal<MasterRef | null>(null);
  readonly unit = signal<MasterRef | null>(null);
  readonly decimalHint = computed(() =>
    this.unit()?.decimalAllowed
      ? 'This unit allows decimal quantities.'
      : 'This unit stores whole quantities.',
  );
  private hydrating = false;

  readonly form = this.formBuilder.nonNullable.group({
    productCode: ['', [Validators.required, Validators.pattern(/^[A-Za-z0-9][A-Za-z0-9._-]{1,63}$/)]],
    barcode: ['', Validators.pattern(/^$|^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$/)],
    name: ['', [Validators.required, Validators.maxLength(200)]],
    shortName: ['', Validators.maxLength(80)],
    description: ['', Validators.maxLength(2000)],
    categoryId: this.formBuilder.control<number | null>(null, Validators.required),
    brandId: this.formBuilder.control<number | null>(null, Validators.required),
    unitId: this.formBuilder.control<number | null>(null, Validators.required),
    taxPercent: ['0', [Validators.required, Validators.pattern(MONEY)]],
    purchasePrice: ['0', [Validators.required, Validators.pattern(MONEY)]],
    sellingPrice: ['0', [Validators.required, Validators.pattern(MONEY)]],
    mrp: ['0', [Validators.required, Validators.pattern(MONEY)]],
    minimumSellingPrice: ['0', [Validators.required, Validators.pattern(MONEY)]],
    reorderLevel: ['0', [Validators.required, Validators.pattern(MONEY)]],
    maximumStockLevel: ['', Validators.pattern(/^$|^\d+(\.\d{1,4})?$/)],
    stockType: this.formBuilder.nonNullable.control<StockType>('NONE', Validators.required),
    batchTracking: [false],
    serialTracking: [false],
    expiryTracking: [false],
    status: this.formBuilder.nonNullable.control<RecordStatus>('ACTIVE', Validators.required),
  });

  constructor() {
    this.applyTracking('NONE', false);
    this.form.controls.stockType.valueChanges.pipe(takeUntilDestroyed()).subscribe((stockType) => {
      if (!this.hydrating) {
        this.applyTracking(stockType, true);
      }
    });
    const requested = this.route.snapshot.queryParamMap.get('tab');
    if (requested === 'edit' || (requested === 'inventory' && this.auth.hasPermission('INVENTORY_VIEW'))) {
      this.tab.set(requested);
    }
    const idParam = this.route.snapshot.paramMap.get('id');
    const id = idParam ? Number(idParam) : null;
    if (idParam && !Number.isInteger(id)) {
      void this.router.navigate(['/products']);
      return;
    }
    if (id == null) {
      this.loading.set(false);
      return;
    }
    this.productId.set(id);
    this.api.get(id).subscribe({
      next: (product) => {
        this.fill(product);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.loading.set(false);
        this.notifications.error(readApiError(error));
        void this.router.navigate(['/products']);
      },
    });
  }

  get editing(): boolean {
    return this.productId() !== null;
  }

  get canSave(): boolean {
    return this.auth.hasPermission(this.editing ? 'PRODUCT_UPDATE' : 'PRODUCT_CREATE');
  }

  get canViewStock(): boolean {
    return this.auth.hasPermission('INVENTORY_VIEW');
  }

  get expiryEnabled(): boolean {
    const stockType = this.form.controls.stockType.value;
    return stockType === 'BATCH' || stockType === 'SERIAL';
  }

  hasUnsavedChanges(): boolean {
    return this.form.dirty && !this.saving();
  }

  @HostListener('window:beforeunload', ['$event'])
  protect(event: BeforeUnloadEvent): void {
    if (this.hasUnsavedChanges()) {
      event.preventDefault();
    }
  }

  invalid(name: 'productCode' | 'barcode' | 'name' | 'shortName' | 'description' | 'categoryId' | 'brandId' | 'unitId' | 'taxPercent' | 'purchasePrice' | 'sellingPrice' | 'mrp' | 'minimumSellingPrice' | 'reorderLevel' | 'maximumStockLevel' | 'stockType'): boolean {
    const control = this.form.controls[name];
    return control.invalid && (control.touched || this.form.touched);
  }

  message(name: 'productCode' | 'barcode' | 'name' | 'categoryId' | 'brandId' | 'unitId' | 'taxPercent' | 'purchasePrice' | 'sellingPrice' | 'mrp' | 'minimumSellingPrice' | 'reorderLevel' | 'maximumStockLevel' | 'stockType', fallback: string): string {
    const control = this.form.controls[name];
    return control.hasError('server') ? String(control.getError('server')) : fallback;
  }

  stockLabel(value: string): string {
    const labels: Record<string, string> = { NONE: 'None', BATCH: 'Batch', SERIAL: 'Serial', PICS: 'Piece' };
    return labels[value] ?? value;
  }

  openTab(next: 'overview' | 'inventory' | 'edit'): void {
    if (next === 'inventory' && !this.canViewStock) {
      return;
    }
    if (this.tab() === 'edit' && next !== 'edit' && this.hasUnsavedChanges()) {
      this.confirmation
        .confirm({
          title: 'Leave without saving?',
          message: 'This product has unsaved changes.',
          confirmLabel: 'Leave',
          cancelLabel: 'Stay',
          destructive: true,
        })
        .subscribe((confirmed) => {
          if (confirmed) {
            this.tab.set(next);
            this.prepareTab(next);
          }
        });
      return;
    }
    this.tab.set(next);
    this.prepareTab(next);
  }

  chooseCategory(value: MasterRef | null): void {
    this.category.set(value);
    this.form.controls.categoryId.setValue(value?.id ?? null);
    this.form.controls.categoryId.markAsDirty();
  }

  chooseBrand(value: MasterRef | null): void {
    this.brand.set(value);
    this.form.controls.brandId.setValue(value?.id ?? null);
    this.form.controls.brandId.markAsDirty();
  }

  chooseUnit(value: MasterRef | null): void {
    this.unit.set(value);
    this.form.controls.unitId.setValue(value?.id ?? null);
    this.form.controls.unitId.markAsDirty();
  }

  submit(): void {
    if (!this.canSave) {
      return;
    }
    const value = this.form.getRawValue();
    const ruleErrors = productFieldErrors({
      purchasePrice: value.purchasePrice,
      sellingPrice: value.sellingPrice,
      mrp: value.mrp,
      minimumSellingPrice: value.minimumSellingPrice,
      reorderLevel: value.reorderLevel,
      maximumStockLevel: value.maximumStockLevel,
      stockType: value.stockType,
      batchTracking: value.batchTracking,
      serialTracking: value.serialTracking,
      expiryTracking: value.expiryTracking,
      decimalAllowed: this.unit()?.decimalAllowed === true,
    });
    for (const [field, text] of Object.entries(ruleErrors)) {
      this.form.get(field)?.setErrors({ server: text });
    }
    if (this.form.invalid || Object.keys(ruleErrors).length > 0) {
      this.form.markAllAsTouched();
      if (this.form.controls.barcode.invalid) {
        this.advancedOpen.set(true);
      }
      this.tab.set('edit');
      return;
    }
    if (value.categoryId == null || value.brandId == null || value.unitId == null) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    const body: ProductWrite = {
      productCode: value.productCode.trim(),
      barcode: blankToNull(value.barcode),
      name: value.name.trim(),
      shortName: blankToNull(value.shortName),
      description: blankToNull(value.description),
      categoryId: value.categoryId,
      brandId: value.brandId,
      unitId: value.unitId,
      taxPercent: value.taxPercent,
      purchasePrice: value.purchasePrice,
      sellingPrice: value.sellingPrice,
      mrp: value.mrp,
      minimumSellingPrice: value.minimumSellingPrice,
      reorderLevel: value.reorderLevel,
      maximumStockLevel: blankToNull(value.maximumStockLevel),
      stockType: value.stockType,
      batchTracking: value.batchTracking,
      serialTracking: value.serialTracking,
      expiryTracking: value.expiryTracking,
      status: value.status,
    };
    const request = this.editing
      ? this.api.update(this.productId()!, { ...body, version: this.version() ?? 0 })
      : this.api.create(body);
    request.subscribe({
      next: () => {
        this.form.markAsPristine();
        this.saving.set(false);
        this.notifications.success(this.editing ? 'Product updated' : 'Product created');
        void this.router.navigate(['/products']);
      },
      error: (error: unknown) => {
        this.saving.set(false);
        for (const detail of readFieldErrors(error)) {
          if (detail.field) {
            this.form.get(detail.field)?.setErrors({ server: detail.message });
          }
        }
        this.notifications.error(readApiError(error));
      },
    });
  }

  resetForm(): void {
    const id = this.productId();
    if (id == null) {
      this.hydrating = true;
      this.form.reset({
        productCode: '',
        barcode: '',
        name: '',
        shortName: '',
        description: '',
        categoryId: null,
        brandId: null,
        unitId: null,
        taxPercent: '0',
        purchasePrice: '0',
        sellingPrice: '0',
        mrp: '0',
        minimumSellingPrice: '0',
        reorderLevel: '0',
        maximumStockLevel: '',
        stockType: 'NONE',
        batchTracking: false,
        serialTracking: false,
        expiryTracking: false,
        status: 'ACTIVE',
      });
      this.category.set(null);
      this.brand.set(null);
      this.unit.set(null);
      this.applyTracking('NONE', false);
      this.hydrating = false;
      return;
    }
    this.loading.set(true);
    this.api.get(id).subscribe({
      next: (product) => {
        this.fill(product);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.loading.set(false);
        this.notifications.error(readApiError(error));
      },
    });
  }

  remove(): void {
    const id = this.productId();
    if (id == null) {
      return;
    }
    this.confirmation
      .confirm({
        title: 'Delete product',
        message: 'Delete this product from the active catalog? Historical sales and purchases keep their own copy of the line.',
        confirmLabel: 'Delete',
        cancelLabel: 'Keep',
        destructive: true,
      })
      .subscribe((confirmed) => {
        if (!confirmed) {
          return;
        }
        this.api.delete(id).subscribe({
          next: () => {
            this.form.markAsPristine();
            this.notifications.success('Product deleted');
            void this.router.navigate(['/products']);
          },
          error: (error: unknown) => this.notifications.error(readApiError(error)),
        });
      });
  }

  private fill(product: ProductRecord): void {
    this.hydrating = true;
    this.record.set(product);
    this.version.set(product.version);
    this.advancedOpen.set(Boolean(product.barcode) || product.batchTracking || product.serialTracking || product.expiryTracking);
    this.category.set(product.category);
    this.brand.set(product.brand);
    this.unit.set({
      id: product.unit.id,
      code: product.unit.shortName,
      name: product.unit.name,
      decimalAllowed: product.unit.decimalAllowed,
    });
    this.form.reset({
      productCode: product.productCode,
      barcode: product.barcode ?? '',
      name: product.name,
      shortName: product.shortName ?? '',
      description: product.description ?? '',
      categoryId: product.category.id,
      brandId: product.brand.id,
      unitId: product.unit.id,
      taxPercent: String(product.taxPercent),
      purchasePrice: String(product.purchasePrice),
      sellingPrice: String(product.sellingPrice),
      mrp: String(product.mrp),
      minimumSellingPrice: String(product.minimumSellingPrice),
      reorderLevel: String(product.reorderLevel),
      maximumStockLevel: product.maximumStockLevel == null ? '' : String(product.maximumStockLevel),
      stockType: product.stockType,
      batchTracking: product.batchTracking,
      serialTracking: product.serialTracking,
      expiryTracking: product.expiryTracking,
      status: product.status,
    });
    this.applyTracking(product.stockType, false);
    this.form.controls.expiryTracking.setValue(product.expiryTracking, { emitEvent: false });
    this.form.markAsPristine();
    this.hydrating = false;
    if (this.tab() === 'inventory') {
      this.loadStock();
    }
  }

  private prepareTab(next: 'overview' | 'inventory' | 'edit'): void {
    if (next === 'inventory') {
      this.loadStock();
    }
  }

  loadStock(): void {
    const id = this.productId();
    if (id == null || !this.canViewStock) {
      return;
    }
    this.stockLoading.set(true);
    this.stockFailed.set(false);
    this.inventory.product(id, null).subscribe({
      next: (stock) => {
        this.stock.set(stock);
        this.stockLoading.set(false);
      },
      error: () => {
        this.stockLoading.set(false);
        this.stockFailed.set(true);
      },
    });
  }

  private applyTracking(stockType: StockType, keepExpiry: boolean): void {
    const flags = trackingFor(stockType);
    if (keepExpiry && (stockType === 'BATCH' || stockType === 'SERIAL')) {
      flags.expiryTracking = this.form.controls.expiryTracking.value;
    }
    this.form.patchValue(flags, { emitEvent: false });
    this.form.controls.batchTracking.disable({ emitEvent: false });
    this.form.controls.serialTracking.disable({ emitEvent: false });
    if (stockType === 'BATCH' || stockType === 'SERIAL') {
      this.form.controls.expiryTracking.enable({ emitEvent: false });
    } else {
      this.form.controls.expiryTracking.disable({ emitEvent: false });
    }
  }
}

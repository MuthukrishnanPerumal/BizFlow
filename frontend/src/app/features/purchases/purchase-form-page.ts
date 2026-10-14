import { AfterViewInit, ChangeDetectionStrategy, Component, DestroyRef, ElementRef, HostListener, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormArray, FormBuilder, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged, of, Subject, switchMap } from 'rxjs';

import { UnsavedForm } from '../../core/guards/unsaved-changes.guard';
import { PartyRecord, ProductRecord } from '../../core/models/master.model';
import { NotificationService } from '../../core/services/notification.service';
import { explainError } from '../../core/utils/http-error.util';
import { formatMoney } from '../master/master-format';
import { PartyApi } from '../master/party-api.service';
import { ProductApi } from '../products/product-api.service';
import { SaleApi } from '../sales/sale-api.service';
import { PageHeader } from '../../shared/components/page-header/page-header';
import { SkeletonLoader } from '../../shared/components/skeleton-loader/skeleton-loader';
import { PurchaseApi } from './purchase-api.service';
import { amount, moneyText, previewDocument } from './purchase-money';
import { PaymentMethod, PurchaseWrite } from './purchase.model';

interface LineControls {
  productId: FormControl<number | null>;
  label: FormControl<string>;
  barcode: FormControl<string>;
  batch: FormControl<string>;
  expiryDate: FormControl<string>;
  quantity: FormControl<string>;
  freeQuantity: FormControl<string>;
  purchasePrice: FormControl<string>;
  discount: FormControl<string>;
  tax: FormControl<string>;
  serials: FormControl<string>;
}

interface LineMeta {
  batchTracking: boolean;
  serialTracking: boolean;
  expiryTracking: boolean;
  decimalAllowed: boolean;
}

@Component({
  selector: 'app-purchase-form-page',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    RouterLink,
    PageHeader,
    SkeletonLoader,
  ],
  templateUrl: './purchase-form-page.html',
  styleUrls: ['./purchase-screen.scss', '../sales/transaction-workspace.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PurchaseFormPage implements UnsavedForm, AfterViewInit {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(PurchaseApi);
  private readonly products = inject(ProductApi);
  private readonly stockApi = inject(SaleApi);
  private readonly parties = inject(PartyApi);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly notifications = inject(NotificationService);
  private readonly productQuery = new Subject<{ index: number; term: string }>();

  readonly barcodeInput = viewChild<ElementRef<HTMLInputElement>>('barcodeInput');
  readonly money = formatMoney;
  readonly methods: PaymentMethod[] = ['CASH', 'CREDIT', 'BANK', 'UPI', 'CHEQUE', 'CARD'];
  readonly barcode = new FormControl('', { nonNullable: true });
  readonly supplierSearch = new FormControl('', { nonNullable: true });
  readonly productSearch = new FormControl('', { nonNullable: true });
  readonly supplierHits = signal<PartyRecord[]>([]);
  readonly productHits = signal<ProductRecord[]>([]);
  readonly quickHits = signal<ProductRecord[]>([]);
  readonly hitStock = signal<Record<number, string>>({});
  readonly activeRow = signal<number | null>(null);
  readonly meta = signal<LineMeta[]>([]);
  readonly preview = signal(previewDocument([], '0', '0', '0', '0'));
  readonly saving = signal(false);
  readonly saved = signal(false);
  readonly loading = signal(false);
  readonly errorMessage = signal('');
  readonly purchaseId = signal<number | null>(null);
  readonly version = signal<number | null>(null);

  readonly form = this.fb.group({
    supplierId: this.fb.control<number | null>(null, Validators.required),
    invoiceNumber: this.fb.nonNullable.control('', Validators.required),
    purchaseDate: this.fb.nonNullable.control(today(), Validators.required),
    paymentType: this.fb.nonNullable.control<PaymentMethod>('CASH', Validators.required),
    referenceNumber: this.fb.nonNullable.control(''),
    notes: this.fb.nonNullable.control(''),
    billDiscount: this.fb.nonNullable.control('0'),
    additionalCharges: this.fb.nonNullable.control('0'),
    roundOff: this.fb.nonNullable.control('0'),
    paidAmount: this.fb.nonNullable.control('0'),
    items: this.fb.array<FormGroup<LineControls>>([]),
  });

  constructor() {
    const destroyRef = inject(DestroyRef);
    this.addLine();
    this.form.valueChanges.pipe(takeUntilDestroyed(destroyRef)).subscribe(() => this.refreshPreview());
    this.supplierSearch.valueChanges
      .pipe(
        debounceTime(250),
        distinctUntilChanged(),
        switchMap((term) =>
          term.trim().length === 0
            ? of({ items: [] as PartyRecord[] })
            : this.parties.list('suppliers', term.trim(), 'ACTIVE', 0, 'supplierName,asc', 8),
        ),
        takeUntilDestroyed(destroyRef),
      )
      .subscribe((page) => this.supplierHits.set(page.items));
    this.productSearch.valueChanges
      .pipe(
        debounceTime(200),
        distinctUntilChanged(),
        switchMap((term) =>
          term.trim().length < 1 ? of({ items: [] as ProductRecord[] }) : this.products.list(term.trim(), 'ACTIVE', '', null, 0, 'name,asc', 8),
        ),
        takeUntilDestroyed(destroyRef),
      )
      .subscribe((page) => {
        this.quickHits.set(page.items);
        this.rememberStock(page.items);
      });
    this.productQuery
      .pipe(
        debounceTime(250),
        switchMap((query) => {
          if (query.term.trim().length < 2) {
            return of({ index: query.index, items: [] as ProductRecord[] });
          }
          return this.products
            .list(query.term.trim(), 'ACTIVE', '', null, 0, 'name,asc', 8)
            .pipe(switchMap((page) => of({ index: query.index, items: page.items })));
        }),
        takeUntilDestroyed(destroyRef),
      )
      .subscribe((result) => {
        if (this.activeRow() === result.index) {
          this.productHits.set(result.items);
        }
      });
    const id = this.route.snapshot.paramMap.get('id');
    if (id && id !== 'new') {
      this.load(Number(id));
    }
    this.refreshPreview();
  }

  ngAfterViewInit(): void {
    queueMicrotask(() => document.getElementById('purchase-supplier-search')?.focus());
  }

  @HostListener('document:keydown', ['$event'])
  onKey(event: KeyboardEvent): void {
    if (event.key === 'F2') {
      event.preventDefault();
      this.barcodeInput()?.nativeElement.focus();
    }
    if (event.key === 'F9' || (event.ctrlKey && event.key === 'Enter')) {
      event.preventDefault();
      this.save(true);
    }
  }

  get lines(): FormArray<FormGroup<LineControls>> {
    return this.form.controls.items;
  }

  hasUnsavedChanges(): boolean {
    return this.form.dirty && !this.saved();
  }

  takeSupplier(event: Event): void {
    event.preventDefault();
    const supplier = this.supplierHits()[0];
    if (supplier) {
      this.chooseSupplier(supplier);
    }
  }

  clearSupplier(): void {
    this.form.controls.supplierId.setValue(null);
    this.form.controls.supplierId.markAsDirty();
    this.supplierSearch.setValue('');
    this.supplierHits.set([]);
    queueMicrotask(() => document.getElementById('purchase-supplier-search')?.focus());
  }

  takeProduct(event: Event): void {
    event.preventDefault();
    const product = this.quickHits()[0];
    if (product) {
      this.addProduct(product);
    }
  }

  addProduct(product: ProductRecord): void {
    const emptyIndex = this.lines.controls.findIndex((row) => row.controls.productId.value == null);
    if (emptyIndex >= 0) {
      this.chooseProduct(emptyIndex, product);
    } else {
      this.addLine(product);
    }
    this.productSearch.setValue('', { emitEvent: false });
    this.quickHits.set([]);
    this.barcodeInput()?.nativeElement.focus();
  }

  chooseSupplier(supplier: PartyRecord): void {
    this.form.controls.supplierId.setValue(supplier.id);
    this.form.controls.supplierId.markAsDirty();
    this.supplierSearch.setValue(supplier.supplierName ?? '', { emitEvent: false });
    this.supplierHits.set([]);
  }

  searchProducts(index: number, term: string): void {
    this.activeRow.set(index);
    this.productQuery.next({ index, term });
  }

  chooseProduct(index: number, product: ProductRecord): void {
    const row = this.lines.at(index);
    row.patchValue({
      productId: product.id,
      label: `${product.name} · ${product.productCode}`,
      barcode: product.barcode ?? '',
      purchasePrice: moneyText(amount(product.purchasePrice)),
      tax: moneyText(amount(product.taxPercent)),
    });
    this.meta.update((rows) => rows.map((item, itemIndex) => (itemIndex === index ? metaOf(product) : item)));
    this.productHits.set([]);
    this.activeRow.set(null);
    this.refreshPreview();
  }

  addLine(product?: ProductRecord): void {
    this.lines.push(this.lineGroup(product));
    this.meta.update((rows) => [...rows, product ? metaOf(product) : emptyMeta()]);
    this.refreshPreview();
  }

  removeLine(index: number): void {
    if (this.lines.length === 1) {
      return;
    }
    this.lines.removeAt(index);
    this.meta.update((rows) => rows.filter((_, itemIndex) => itemIndex !== index));
    this.form.markAsDirty();
    this.refreshPreview();
  }

  scanBarcode(event: Event): void {
    event.preventDefault();
    const code = this.barcode.value.trim();
    if (!code) {
      return;
    }
    this.products.lookup(code).subscribe({
      next: (product) => {
        const emptyIndex = this.lines.controls.findIndex((row) => row.controls.productId.value == null);
        if (emptyIndex >= 0) {
          this.chooseProduct(emptyIndex, product);
        } else {
          this.addLine(product);
        }
        this.barcode.setValue('');
        this.barcodeInput()?.nativeElement.focus();
      },
      error: (error: unknown) => {
        const message = explainError(error);
        this.errorMessage.set(message);
        this.notifications.error(message);
      },
    });
  }

  lineError(index: number): string | null {
    const row = this.lines.at(index);
    const flags = this.meta()[index] ?? emptyMeta();
    if (row.controls.productId.value == null) {
      return 'Choose a product';
    }
    const quantity = amount(row.controls.quantity.value);
    const free = amount(row.controls.freeQuantity.value);
    if (!(quantity > 0)) {
      return 'Quantity must be greater than zero';
    }
    if (free < 0 || amount(row.controls.purchasePrice.value) < 0 || amount(row.controls.discount.value) < 0) {
      return 'Amounts cannot be negative';
    }
    const tax = amount(row.controls.tax.value);
    if (tax < 0 || tax > 100) {
      return 'Tax must be between 0 and 100';
    }
    if (!flags.decimalAllowed && (!whole(quantity) || !whole(free))) {
      return 'This unit uses whole quantities';
    }
    if (flags.batchTracking && !row.controls.batch.value.trim()) {
      return 'Batch is required';
    }
    if (flags.expiryTracking && !row.controls.expiryDate.value) {
      return 'Expiry date is required';
    }
    if (flags.serialTracking) {
      const serials = splitSerials(row.controls.serials.value);
      if (serials.length !== quantity + free) {
        return 'Enter one serial number for each unit, including free quantity';
      }
    }
    return null;
  }

  lineTotal(index: number): number {
    return this.preview().lines[index]?.total ?? 0;
  }

  canSave(): boolean {
    if (this.saving() || this.form.controls.supplierId.invalid || this.form.controls.invoiceNumber.invalid) {
      return false;
    }
    if (!this.form.controls.purchaseDate.value || this.lines.length === 0) {
      return false;
    }
    if (this.lines.controls.some((_, index) => this.lineError(index) !== null)) {
      return false;
    }
    const preview = this.preview();
    return Math.abs(preview.roundOff) <= 1 && preview.paidAmount <= preview.grandTotal + 0.0001 && preview.grandTotal >= 0;
  }

  roundToRupee(): void {
    const current = this.preview();
    const exact = current.grandTotal - current.roundOff;
    const roundOff = Math.round(exact) - exact;
    if (Math.abs(roundOff) <= 1) {
      this.form.controls.roundOff.setValue(moneyText(roundOff));
      this.form.markAsDirty();
    }
  }

  payInFull(): void {
    this.form.controls.paidAmount.setValue(moneyText(Math.max(0, this.preview().grandTotal)));
    this.form.markAsDirty();
  }

  save(confirm: boolean): void {
    if (!this.canSave()) {
      this.form.markAllAsTouched();
      this.errorMessage.set('Complete the supplier, invoice, and item lines before saving.');
      return;
    }
    this.saving.set(true);
    this.errorMessage.set('');
    const body = this.payload(confirm);
    const id = this.purchaseId();
    const request = id == null ? this.api.create(body) : this.api.update(id, body);
    request.subscribe({
      next: (purchase) => {
        this.saved.set(true);
        this.notifications.success(confirm ? 'Purchase posted' : 'Purchase draft saved');
        void this.router.navigate(['/purchases', purchase.id]);
      },
      error: (error: unknown) => {
        this.saving.set(false);
        this.errorMessage.set(explainError(error));
      },
    });
  }

  private load(id: number): void {
    this.loading.set(true);
    this.api.get(id).subscribe({
      next: (purchase) => {
        if (!purchase.editable) {
          void this.router.navigate(['/purchases', purchase.id]);
          return;
        }
        this.purchaseId.set(purchase.id);
        this.version.set(purchase.version);
        this.form.patchValue({
          supplierId: purchase.supplier.id,
          invoiceNumber: purchase.invoiceNumber,
          purchaseDate: purchase.purchaseDate,
          paymentType: purchase.paymentType,
          referenceNumber: purchase.referenceNumber ?? '',
          notes: purchase.notes ?? '',
          billDiscount: moneyText(amount(purchase.billDiscount)),
          additionalCharges: moneyText(amount(purchase.additionalCharges)),
          roundOff: moneyText(amount(purchase.roundOff)),
          paidAmount: moneyText(amount(purchase.paidAmount)),
        });
        this.supplierSearch.setValue(purchase.supplier.supplierName, { emitEvent: false });
        this.lines.clear();
        this.meta.set([]);
        for (const item of purchase.items) {
          this.lines.push(
            this.lineGroup(undefined, {
              productId: item.productId,
              label: `${item.productName} · ${item.productCode}`,
              barcode: item.barcode ?? '',
              batch: item.batch ?? '',
              expiryDate: item.expiryDate ?? '',
              quantity: moneyText(amount(item.quantity)),
              freeQuantity: moneyText(amount(item.freeQuantity)),
              purchasePrice: moneyText(amount(item.purchasePrice)),
              discount: moneyText(amount(item.discount)),
              tax: moneyText(amount(item.tax)),
              serials: item.serials.join(', '),
            }),
          );
          this.meta.update((rows) => [
            ...rows,
            {
              batchTracking: item.batchTracking,
              serialTracking: item.serialTracking,
              expiryTracking: item.expiryTracking,
              decimalAllowed: item.decimalAllowed,
            },
          ]);
        }
        this.form.markAsPristine();
        this.loading.set(false);
        this.refreshPreview();
      },
      error: (error: unknown) => {
        this.loading.set(false);
        this.errorMessage.set(explainError(error));
      },
    });
  }

  private payload(confirm: boolean): PurchaseWrite {
    const value = this.form.getRawValue();
    return {
      supplierId: value.supplierId ?? 0,
      invoiceNumber: value.invoiceNumber.trim(),
      purchaseDate: value.purchaseDate,
      paymentType: value.paymentType,
      referenceNumber: blank(value.referenceNumber),
      notes: blank(value.notes),
      billDiscount: moneyText(amount(value.billDiscount)),
      additionalCharges: moneyText(amount(value.additionalCharges)),
      roundOff: moneyText(amount(value.roundOff)),
      paidAmount: moneyText(amount(value.paidAmount)),
      confirm,
      version: this.version() ?? undefined,
      items: value.items.map((item) => ({
        productId: item.productId ?? 0,
        barcode: blank(item.barcode),
        batch: blank(item.batch),
        expiryDate: item.expiryDate || null,
        quantity: moneyText(amount(item.quantity)),
        freeQuantity: moneyText(amount(item.freeQuantity)),
        purchasePrice: moneyText(amount(item.purchasePrice)),
        discount: moneyText(amount(item.discount)),
        tax: moneyText(amount(item.tax)),
        serials: splitSerials(item.serials),
      })),
    };
  }

  private rememberStock(products: ProductRecord[]): void {
    for (const product of products) {
      if (product.stockType === 'NONE') {
        this.hitStock.update((current) => ({ ...current, [product.id]: 'Not tracked' }));
        continue;
      }
      this.stockApi.stock(product.id).subscribe({
        next: (buckets) => {
          const total = buckets.reduce((sum, bucket) => sum + amount(bucket.availableQuantity ?? bucket.quantityOnHand), 0);
          this.hitStock.update((current) => ({ ...current, [product.id]: displayQty(moneyText(total)) }));
        },
        error: () => this.hitStock.update((current) => ({ ...current, [product.id]: '—' })),
      });
    }
  }

  private refreshPreview(): void {
    const value = this.form.getRawValue();
    this.preview.set(
      previewDocument(value.items, value.billDiscount, value.additionalCharges, value.roundOff, value.paidAmount),
    );
  }

  private lineGroup(product?: ProductRecord, seed?: Partial<LineControls extends never ? never : Record<string, string | number | null>>): FormGroup<LineControls> {
    return this.fb.group<LineControls>({
      productId: this.fb.control(product?.id ?? (seed?.['productId'] as number | null) ?? null, Validators.required),
      label: this.fb.nonNullable.control(product ? `${product.name} · ${product.productCode}` : String(seed?.['label'] ?? '')),
      barcode: this.fb.nonNullable.control(product?.barcode ?? String(seed?.['barcode'] ?? '')),
      batch: this.fb.nonNullable.control(String(seed?.['batch'] ?? '')),
      expiryDate: this.fb.nonNullable.control(String(seed?.['expiryDate'] ?? '')),
      quantity: this.fb.nonNullable.control(String(seed?.['quantity'] ?? '1')),
      freeQuantity: this.fb.nonNullable.control(String(seed?.['freeQuantity'] ?? '0')),
      purchasePrice: this.fb.nonNullable.control(product ? moneyText(amount(product.purchasePrice)) : String(seed?.['purchasePrice'] ?? '0')),
      discount: this.fb.nonNullable.control(String(seed?.['discount'] ?? '0')),
      tax: this.fb.nonNullable.control(product ? moneyText(amount(product.taxPercent)) : String(seed?.['tax'] ?? '0')),
      serials: this.fb.nonNullable.control(String(seed?.['serials'] ?? '')),
    });
  }
}

function metaOf(product: ProductRecord): LineMeta {
  return {
    batchTracking: product.batchTracking,
    serialTracking: product.serialTracking,
    expiryTracking: product.expiryTracking,
    decimalAllowed: product.unit.decimalAllowed,
  };
}

function emptyMeta(): LineMeta {
  return { batchTracking: false, serialTracking: false, expiryTracking: false, decimalAllowed: true };
}

function splitSerials(value: string): string[] {
  return value
    .split(/[\s,]+/)
    .map((item) => item.trim())
    .filter((item) => item.length > 0);
}

function blank(value: string): string | null {
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : null;
}

function whole(value: number): boolean {
  return Math.abs(value - Math.round(value)) < 0.00001;
}

function displayQty(value: string | number): string {
  const number = amount(value);
  return Number.isInteger(number) ? String(number) : String(number);
}

function today(): string {
  const now = new Date();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${now.getFullYear()}-${month}-${day}`;
}

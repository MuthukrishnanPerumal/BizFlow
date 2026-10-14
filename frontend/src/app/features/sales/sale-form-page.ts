import { AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, HostListener, computed, inject, signal, viewChild, viewChildren } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormArray, FormBuilder, FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged, of, switchMap } from 'rxjs';

import { UnsavedForm } from '../../core/guards/unsaved-changes.guard';
import { PartyRecord, ProductRecord } from '../../core/models/master.model';
import { NotificationService } from '../../core/services/notification.service';
import { explainError } from '../../core/utils/http-error.util';
import { formatMoney } from '../master/master-format';
import { PartyApi } from '../master/party-api.service';
import { ProductApi } from '../products/product-api.service';
import { PageHeader } from '../../shared/components/page-header/page-header';
import { SaleApi } from './sale-api.service';
import { amount, moneyText, previewSale } from './sale-money';
import { SalePaymentType, SaleWrite, TenderMethod } from './sale.model';

interface LineMeta {
  batchTracking: boolean;
  serialTracking: boolean;
  expiryTracking: boolean;
  onHand: string;
  minimumPrice: string;
  mrp: string;
}

@Component({
  selector: 'app-sale-form-page',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    RouterLink,
    PageHeader,
  ],
  templateUrl: './sale-form-page.html',
  styleUrls: ['./sale-screen.scss', './transaction-workspace.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SaleFormPage implements UnsavedForm, AfterViewInit {
  private readonly api = inject(SaleApi);
  private readonly products = inject(ProductApi);
  private readonly parties = inject(PartyApi);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly notify = inject(NotificationService);
  private readonly fb = inject(FormBuilder);
  private readonly saved = signal(false);
  private readonly revision = signal(0);

  readonly money = formatMoney;
  readonly barcodeInput = viewChild<ElementRef<HTMLInputElement>>('barcodeInput');
  readonly quantityInputs = viewChildren<ElementRef<HTMLInputElement>>('quantityInput');
  readonly saleId = signal<number | null>(null);
  readonly version = signal<number | null>(null);
  readonly saving = signal(false);
  readonly errorMessage = signal('');
  readonly customerHits = signal<PartyRecord[]>([]);
  readonly productHits = signal<ProductRecord[]>([]);
  readonly hitStock = signal<Record<number, string>>({});
  readonly selectedCustomer = signal<PartyRecord | null>(null);
  readonly lineMeta = signal<LineMeta[]>([]);
  readonly barcode = new FormControl('', { nonNullable: true });
  readonly customerSearch = new FormControl('', { nonNullable: true });
  readonly productSearch = new FormControl('', { nonNullable: true });
  readonly invoiceDate = new FormControl(today(), { nonNullable: true });
  readonly paymentType = new FormControl<SalePaymentType>('CASH', { nonNullable: true });
  readonly referenceNumber = new FormControl('', { nonNullable: true });
  readonly notes = new FormControl('', { nonNullable: true });
  readonly orderDiscount = new FormControl('0', { nonNullable: true });
  readonly additionalCharges = new FormControl('0', { nonNullable: true });
  readonly roundOff = new FormControl('0', { nonNullable: true });
  readonly paidAmount = new FormControl('0', { nonNullable: true });
  readonly lines = new FormArray<FormGroup>([]);
  readonly tenders = new FormArray<FormGroup>([]);
  readonly preview = computed(() => {
    this.revision();
    return previewSale(
      this.lines.controls.map((line) => ({
        quantity: String(line.get('quantity')?.value ?? ''),
        sellingPrice: String(line.get('sellingPrice')?.value ?? ''),
        discount: String(line.get('discount')?.value ?? ''),
        tax: String(line.get('tax')?.value ?? ''),
      })),
      this.orderDiscount.value,
      this.additionalCharges.value,
      this.roundOff.value,
      this.paidAmount.value,
    );
  });

  constructor() {
    const refresh = () => this.revision.update((value) => value + 1);
    this.invoiceDate.valueChanges.pipe(takeUntilDestroyed()).subscribe(refresh);
    this.paymentType.valueChanges.pipe(takeUntilDestroyed()).subscribe(refresh);
    this.orderDiscount.valueChanges.pipe(takeUntilDestroyed()).subscribe(refresh);
    this.additionalCharges.valueChanges.pipe(takeUntilDestroyed()).subscribe(refresh);
    this.roundOff.valueChanges.pipe(takeUntilDestroyed()).subscribe(refresh);
    this.paidAmount.valueChanges.pipe(takeUntilDestroyed()).subscribe(refresh);
    this.lines.valueChanges.pipe(takeUntilDestroyed()).subscribe(refresh);
    this.tenders.valueChanges.pipe(takeUntilDestroyed()).subscribe(refresh);
    this.customerSearch.valueChanges
      .pipe(
        debounceTime(200),
        distinctUntilChanged(),
        switchMap((term) =>
          term.trim().length < 1 ? of({ items: [] as PartyRecord[] }) : this.parties.list('customers', term.trim(), 'ACTIVE', 0, 'customerName,asc', 8),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((page) => this.customerHits.set(page.items));
    this.productSearch.valueChanges
      .pipe(
        debounceTime(200),
        distinctUntilChanged(),
        switchMap((term) =>
          term.trim().length < 1 ? of({ items: [] as ProductRecord[] }) : this.products.list(term.trim(), 'ACTIVE', '', null, 0, 'name,asc', 8),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((page) => {
        this.productHits.set(page.items);
        this.rememberStock(page.items);
      });
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.api.get(Number(id)).subscribe({
        next: (sale) => {
          if (!sale.editable) {
            this.saved.set(true);
            void this.router.navigate(['/sales', sale.id]);
            return;
          }
          this.saleId.set(sale.id);
          this.version.set(sale.version);
          this.invoiceDate.setValue(sale.invoiceDate);
          this.paymentType.setValue(sale.paymentType);
          this.referenceNumber.setValue(sale.referenceNumber ?? '');
          this.notes.setValue(sale.notes ?? '');
          this.orderDiscount.setValue(String(sale.orderDiscount));
          this.additionalCharges.setValue(String(sale.additionalCharges));
          this.roundOff.setValue(String(sale.roundOff));
          this.paidAmount.setValue(String(sale.paidAmount));
          this.selectedCustomer.set({
            id: sale.customer.id,
            customerCode: sale.customer.customerCode,
            customerName: sale.customer.customerName,
            phone: sale.customer.phone,
            openingBalance: 0,
            creditLimit: 0,
            status: 'ACTIVE',
            createdAt: '',
            updatedAt: '',
            version: 0,
            account: { debitTotal: 0, creditTotal: 0, balance: sale.customer.balance },
          });
          this.customerSearch.setValue(sale.customer.customerName, { emitEvent: false });
          for (const item of sale.items) {
            this.lines.push(this.lineGroup(item));
            this.lineMeta.update((rows) => [
              ...rows,
              {
                batchTracking: item.batchTracking,
                serialTracking: item.serialTracking,
                expiryTracking: item.expiryTracking,
                onHand: '',
                minimumPrice: '',
                mrp: '',
              },
            ]);
          }
          for (const tender of sale.tenders) {
            this.tenders.push(this.tenderGroup(tender.method, String(tender.amount), tender.referenceNumber ?? ''));
          }
          this.revision.update((value) => value + 1);
        },
        error: (error: unknown) => this.errorMessage.set(explainError(error)),
      });
    }
  }

  ngAfterViewInit(): void {
    queueMicrotask(() => this.barcodeInput()?.nativeElement.focus());
  }

  @HostListener('document:keydown', ['$event'])
  onKey(event: KeyboardEvent): void {
    if (event.key === 'F2') {
      event.preventDefault();
      this.barcodeInput()?.nativeElement.focus();
    }
    if (event.key === 'F4') {
      event.preventDefault();
      document.getElementById('sale-customer-search')?.focus();
    }
    if (event.key === 'F9' || (event.ctrlKey && event.key === 'Enter')) {
      event.preventDefault();
      this.save(true);
    }
  }

  hasUnsavedChanges(): boolean {
    return !this.saved() && (this.lines.length > 0 || !!this.selectedCustomer());
  }

  takeCustomer(event: Event): void {
    event.preventDefault();
    const customer = this.customerHits()[0];
    if (customer) {
      this.chooseCustomer(customer);
    }
  }

  takeProduct(event: Event): void {
    event.preventDefault();
    const product = this.productHits()[0];
    if (product) {
      this.chooseProduct(product);
    }
  }

  stockWarning(index: number): string {
    const available = this.lineMeta()[index]?.onHand ?? '';
    if (!available) {
      return '';
    }
    const quantity = amount(String(this.lines.at(index).get('quantity')?.value ?? ''));
    if (quantity > amount(available)) {
      return `Insufficient stock. Available: ${displayQty(available)}`;
    }
    return '';
  }

  chooseCustomer(customer: PartyRecord): void {
    this.parties.get('customers', customer.id).subscribe({
      next: (loaded) => {
        this.selectedCustomer.set(loaded);
        this.customerSearch.setValue(loaded.customerName ?? '', { emitEvent: false });
        this.customerHits.set([]);
        this.barcodeInput()?.nativeElement.focus();
      },
      error: () => {
        this.selectedCustomer.set(customer);
        this.customerHits.set([]);
      },
    });
  }

  clearCustomer(): void {
    this.selectedCustomer.set(null);
    this.customerSearch.setValue('');
  }

  scan(event: Event): void {
    event.preventDefault();
    const code = this.barcode.value.trim();
    if (!code) {
      return;
    }
    this.products.lookup(code).subscribe({
      next: (product) => {
        this.barcode.setValue('');
        this.addOrIncrement(product);
      },
      error: (error: unknown) => this.errorMessage.set(explainError(error)),
    });
  }

  chooseProduct(product: ProductRecord): void {
    this.productSearch.setValue('', { emitEvent: false });
    this.productHits.set([]);
    this.addOrIncrement(product);
  }

  removeLine(index: number): void {
    this.lines.removeAt(index);
    this.lineMeta.update((rows) => rows.filter((_, row) => row !== index));
    this.revision.update((value) => value + 1);
  }

  addTender(): void {
    this.tenders.push(this.tenderGroup('CASH', '0', ''));
    this.revision.update((value) => value + 1);
  }

  removeTender(index: number): void {
    this.tenders.removeAt(index);
    this.revision.update((value) => value + 1);
  }

  onPaymentType(value: SalePaymentType): void {
    this.paymentType.setValue(value);
    if (value === 'CREDIT') {
      this.paidAmount.setValue('0');
      this.tenders.clear();
    } else if (value !== 'MIXED') {
      this.tenders.clear();
      this.paidAmount.setValue(moneyText(Math.max(0, this.preview().grandTotal)));
    } else if (this.tenders.length === 0) {
      this.addTender();
    }
    this.revision.update((value) => value + 1);
  }

  collectFull(): void {
    this.paidAmount.setValue(moneyText(Math.max(0, this.preview().grandTotal)));
    this.revision.update((value) => value + 1);
  }

  lineAt(index: number): FormGroup {
    return this.lines.at(index);
  }

  tenderAt(index: number): FormGroup {
    return this.tenders.at(index);
  }

  lineTotal(index: number): string {
    return formatMoney(this.preview().lines[index]?.total ?? 0);
  }

  save(confirm: boolean): void {
    const customer = this.selectedCustomer();
    if (!customer) {
      this.errorMessage.set('Choose a customer before saving.');
      return;
    }
    if (this.lines.length === 0) {
      this.errorMessage.set('Add at least one item.');
      return;
    }
    const body = this.payload(customer.id, confirm);
    this.saving.set(true);
    this.errorMessage.set('');
    const request = this.saleId() ? this.api.update(this.saleId()!, body) : this.api.create(body);
    request.subscribe({
      next: (sale) => {
        this.saved.set(true);
        this.saving.set(false);
        this.notify.success(confirm ? 'Sale completed' : 'Draft saved');
        void this.router.navigate(['/sales', sale.id]);
      },
      error: (error: unknown) => {
        this.saving.set(false);
        this.errorMessage.set(explainError(error));
      },
    });
  }

  private addOrIncrement(product: ProductRecord): void {
    if (product.status !== 'ACTIVE') {
      this.errorMessage.set(`${product.name} is inactive`);
      return;
    }
    const existing = this.lines.controls.findIndex((line, index) => {
      const meta = this.lineMeta()[index];
      return Number(line.get('productId')?.value) === product.id && !meta?.serialTracking && !String(line.get('batch')?.value ?? '');
    });
    if (existing >= 0) {
      const control = this.lines.at(existing).get('quantity');
      control?.setValue(moneyText(amount(String(control.value)) + 1));
      this.revision.update((value) => value + 1);
      this.focusQuantity(existing);
      return;
    }
    this.lines.push(
      this.lineGroup({
        productId: product.id,
        label: `${product.name} · ${product.productCode}`,
        barcode: product.barcode ?? '',
        quantity: '1',
        sellingPrice: String(product.sellingPrice),
        discount: '0',
        tax: String(product.taxPercent),
        serials: '',
        batch: '',
        expiryDate: '',
      }),
    );
    const index = this.lines.length - 1;
    this.lineMeta.update((rows) => [
      ...rows,
      {
        batchTracking: product.batchTracking,
        serialTracking: product.serialTracking,
        expiryTracking: product.expiryTracking,
        onHand: '',
        minimumPrice: String(product.minimumSellingPrice),
        mrp: String(product.mrp),
      },
    ]);
    if (product.stockType !== 'NONE') {
      this.api.stock(product.id).subscribe({
        next: (buckets) => {
          const tracked = buckets.filter((bucket) => bucket.defaultLocation);
          const source = tracked.length > 0 ? tracked : buckets;
          const total = source.reduce((sum, bucket) => sum + amount(bucket.availableQuantity ?? bucket.quantityOnHand), 0);
          this.lineMeta.update((rows) => rows.map((row, rowIndex) => (rowIndex === index ? { ...row, onHand: displayQty(moneyText(total)) } : row)));
        },
        error: () => undefined,
      });
    }
    this.revision.update((value) => value + 1);
    this.focusQuantity(index);
  }

  private rememberStock(products: ProductRecord[]): void {
    for (const product of products) {
      if (product.stockType === 'NONE') {
        this.hitStock.update((current) => ({ ...current, [product.id]: 'Not tracked' }));
        continue;
      }
      this.api.stock(product.id).subscribe({
        next: (buckets) => {
          const total = buckets.reduce((sum, bucket) => sum + amount(bucket.availableQuantity ?? bucket.quantityOnHand), 0);
          this.hitStock.update((current) => ({ ...current, [product.id]: displayQty(moneyText(total)) }));
        },
        error: () => this.hitStock.update((current) => ({ ...current, [product.id]: '—' })),
      });
    }
  }

  private focusQuantity(index: number): void {
    queueMicrotask(() => this.quantityInputs().at(index)?.nativeElement.focus());
  }

  private payload(customerId: number, confirm: boolean): SaleWrite {
    const paymentType = this.paymentType.value;
    return {
      customerId,
      invoiceDate: this.invoiceDate.value,
      paymentType,
      referenceNumber: blank(this.referenceNumber.value),
      notes: blank(this.notes.value),
      orderDiscount: this.orderDiscount.value || '0',
      additionalCharges: this.additionalCharges.value || '0',
      roundOff: this.roundOff.value || '0',
      paidAmount: paymentType === 'CREDIT' ? '0' : this.paidAmount.value || '0',
      confirm,
      version: this.version() ?? undefined,
      items: this.lines.controls.map((line) => ({
        productId: Number(line.get('productId')?.value),
        barcode: blank(String(line.get('barcode')?.value ?? '')),
        batch: blank(String(line.get('batch')?.value ?? '')),
        expiryDate: blank(String(line.get('expiryDate')?.value ?? '')),
        quantity: String(line.get('quantity')?.value ?? '0'),
        sellingPrice: String(line.get('sellingPrice')?.value ?? '0'),
        discount: String(line.get('discount')?.value ?? '0'),
        tax: String(line.get('tax')?.value ?? '0'),
        serials: String(line.get('serials')?.value ?? '')
          .split(/[\s,]+/)
          .map((value) => value.trim())
          .filter((value) => value.length > 0),
      })),
      tenders:
        paymentType === 'MIXED'
          ? this.tenders.controls.map((tender) => ({
              method: tender.get('method')?.value as TenderMethod,
              amount: String(tender.get('amount')?.value ?? '0'),
              referenceNumber: blank(String(tender.get('referenceNumber')?.value ?? '')),
            }))
          : null,
    };
  }

  private lineGroup(seed: {
    productId?: number;
    label?: string;
    productName?: string;
    productCode?: string;
    barcode?: string | null;
    batch?: string | null;
    expiryDate?: string | null;
    quantity?: string | number;
    sellingPrice?: string | number;
    discount?: string | number;
    tax?: string | number;
    serials?: string[] | string;
  }): FormGroup {
    return this.fb.group({
      productId: [seed.productId],
      label: [seed.label ?? `${seed.productName ?? ''} · ${seed.productCode ?? ''}`],
      barcode: [seed.barcode ?? ''],
      batch: [seed.batch ?? ''],
      expiryDate: [seed.expiryDate ?? ''],
      quantity: [String(seed.quantity ?? '1')],
      sellingPrice: [String(seed.sellingPrice ?? '0')],
      discount: [String(seed.discount ?? '0')],
      tax: [String(seed.tax ?? '0')],
      serials: [Array.isArray(seed.serials) ? seed.serials.join(' ') : String(seed.serials ?? '')],
    });
  }

  private tenderGroup(method: TenderMethod, value: string, reference: string): FormGroup {
    return this.fb.group({
      method: [method],
      amount: [value],
      referenceNumber: [reference],
    });
  }

}

function today(): string {
  const now = new Date();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${now.getFullYear()}-${month}-${day}`;
}

function displayQty(value: string | number): string {
  const number = amount(value);
  return Number.isInteger(number) ? String(number) : String(number);
}

function blank(value: string): string | null {
  const trimmed = value.trim();
  return trimmed.length === 0 ? null : trimmed;
}

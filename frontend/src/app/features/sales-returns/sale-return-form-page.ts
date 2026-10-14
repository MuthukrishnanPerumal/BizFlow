import { AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, inject, signal, viewChild } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { UnsavedForm } from '../../core/guards/unsaved-changes.guard';
import { NotificationService } from '../../core/services/notification.service';
import { explainError } from '../../core/utils/http-error.util';
import { formatMoney } from '../master/master-format';
import { PageHeader } from '../../shared/components/page-header/page-header';
import { SaleApi } from '../sales/sale-api.service';
import { RefundMethod, SaleDetail } from '../sales/sale.model';

interface DraftLine {
  saleItemId: number;
  productName: string;
  sold: string;
  returned: string;
  remaining: string;
  quantity: string;
  serials: string;
  serialTracking: boolean;
}

@Component({
  selector: 'app-sale-return-form-page',
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule, RouterLink, PageHeader],
  templateUrl: './sale-return-form-page.html',
  styleUrls: ['../sales/sale-screen.scss', '../sales/transaction-workspace.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SaleReturnFormPage implements UnsavedForm, AfterViewInit {
  private readonly api = inject(SaleApi);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly notify = inject(NotificationService);
  private readonly saved = signal(false);

  readonly money = formatMoney;
  readonly invoiceInput = viewChild<ElementRef<HTMLInputElement>>('invoiceInput');
  readonly invoiceSearch = new FormControl('', { nonNullable: true });
  readonly returnDate = new FormControl(today(), { nonNullable: true });
  readonly reason = new FormControl('', { nonNullable: true });
  readonly refundMethod = new FormControl<RefundMethod>('CREDIT', { nonNullable: true });
  readonly notes = new FormControl('', { nonNullable: true });
  readonly sale = signal<SaleDetail | null>(null);
  readonly lines = signal<DraftLine[]>([]);
  readonly saving = signal(false);
  readonly errorMessage = signal('');

  constructor() {
    const saleId = this.route.snapshot.queryParamMap.get('saleId');
    if (saleId) {
      this.loadSale(Number(saleId));
    }
  }

  ngAfterViewInit(): void {
    queueMicrotask(() => this.invoiceInput()?.nativeElement.focus());
  }

  hasUnsavedChanges(): boolean {
    return !this.saved() && this.sale() !== null;
  }

  findInvoice(): void {
    const term = this.invoiceSearch.value.trim();
    if (!term) {
      return;
    }
    this.api.list({ search: term, status: '', paymentType: '', customerId: null, invoiceNumber: term, dateFrom: '', dateTo: '', page: 0, sort: 'invoiceDate,desc' }).subscribe({
      next: (page) => {
        const match = page.items.find((row) => row.invoiceNumber.toLowerCase() === term.toLowerCase()) ?? page.items[0];
        if (!match) {
          this.errorMessage.set('No invoice matches that number.');
          return;
        }
        this.loadSale(match.id);
      },
      error: (error: unknown) => this.errorMessage.set(explainError(error)),
    });
  }

  returningCount(): number {
    return this.lines().filter((line) => Number(line.quantity) > 0).length;
  }

  quantityWarning(line: DraftLine): string {
    const quantity = Number(line.quantity);
    if (!Number.isFinite(quantity) || quantity < 0) {
      return 'Return quantity cannot be negative.';
    }
    if (quantity > Number(line.remaining)) {
      return `Return quantity is above the eligible quantity. Available: ${line.remaining}`;
    }
    return '';
  }

  setQuantity(index: number, value: string): void {
    this.lines.update((rows) => rows.map((row, rowIndex) => (rowIndex === index ? { ...row, quantity: value } : row)));
  }

  setSerials(index: number, value: string): void {
    this.lines.update((rows) => rows.map((row, rowIndex) => (rowIndex === index ? { ...row, serials: value } : row)));
  }

  save(): void {
    const sale = this.sale();
    if (!sale) {
      this.errorMessage.set('Choose the original invoice.');
      return;
    }
    const items = this.lines()
      .filter((line) => Number(line.quantity) > 0)
      .map((line) => ({
        saleItemId: line.saleItemId,
        quantity: line.quantity,
        serials: line.serials.split(/[\s,]+/).map((value) => value.trim()).filter((value) => value.length > 0),
      }));
    if (items.length === 0) {
      this.errorMessage.set('Enter a return quantity for at least one item.');
      return;
    }
    const warning = this.lines().map((line) => this.quantityWarning(line)).find((message) => message.length > 0);
    if (warning) {
      this.errorMessage.set(warning);
      return;
    }
    this.saving.set(true);
    this.errorMessage.set('');
    this.api
      .createReturn({
        saleId: sale.id,
        saleVersion: sale.version,
        returnDate: this.returnDate.value,
        reason: this.reason.value.trim(),
        refundMethod: this.refundMethod.value,
        notes: this.notes.value.trim() || null,
        items,
      })
      .subscribe({
        next: (created) => {
          this.saved.set(true);
          this.saving.set(false);
          this.notify.success('Sales return saved');
          void this.router.navigate(['/sales-returns', created.id]);
        },
        error: (error: unknown) => {
          this.saving.set(false);
          this.errorMessage.set(explainError(error));
        },
      });
  }

  private loadSale(id: number): void {
    this.api.get(id).subscribe({
      next: (sale) => {
        this.sale.set(sale);
        this.invoiceSearch.setValue(sale.invoiceNumber, { emitEvent: false });
        this.lines.set(
          sale.items
            .filter((item) => Number(item.remainingQuantity) > 0)
            .map((item) => ({
              saleItemId: item.id,
              productName: item.productName,
              sold: String(item.quantity),
              returned: String(item.returnedQuantity),
              remaining: String(item.remainingQuantity),
              quantity: '0',
              serials: '',
              serialTracking: item.serialTracking,
            })),
        );
        this.errorMessage.set(sale.returnable ? '' : 'This invoice cannot accept a return.');
      },
      error: (error: unknown) => this.errorMessage.set(explainError(error)),
    });
  }
}

function today(): string {
  const now = new Date();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${now.getFullYear()}-${month}-${day}`;
}

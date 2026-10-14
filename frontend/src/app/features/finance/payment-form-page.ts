import { ChangeDetectionStrategy, Component, DestroyRef, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged, of, switchMap } from 'rxjs';

import { UnsavedForm } from '../../core/guards/unsaved-changes.guard';
import { SystemSnapshot } from '../../core/services/system-snapshot.service';
import { PartyRecord } from '../../core/models/master.model';
import { explainError } from '../../core/utils/http-error.util';
import { blankToNull, formatDate, formatMoney } from '../master/master-format';
import { PartyApi } from '../master/party-api.service';
import { PageHeader } from '../../shared/components/page-header/page-header';
import { StatusBadge } from '../../shared/components/status-badge/status-badge';
import { FinanceApi } from './finance-api.service';
import { FinanceNav } from './finance-nav';
import { allocateOldest, compareMoney, fromScaled, toScaled } from './finance-money';
import { OpenInvoice, PAYMENT_METHODS, PartyKind, PaymentMethod } from './finance.model';

interface AllocationRow {
  invoice: OpenInvoice;
  amount: string;
}

@Component({
  selector: 'app-payment-form-page',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    RouterLink,
    PageHeader,
    StatusBadge,
    FinanceNav,
  ],
  templateUrl: './payment-form-page.html',
  styleUrls: ['../master/master-table.scss', '../../shared/components/data-table/data-table.scss', './finance-screen.scss', '../sales/transaction-workspace.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PaymentFormPage implements UnsavedForm {
  private readonly api = inject(FinanceApi);
  private readonly snapshot = inject(SystemSnapshot);
  private appliedDefault = false;
  private readonly parties = inject(PartyApi);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly money = formatMoney;
  readonly date = formatDate;
  readonly methods = PAYMENT_METHODS;
  readonly party = signal<PartyKind>('customer');
  readonly paymentId = signal<number | null>(null);
  readonly version = signal<number | null>(null);
  readonly partyId = signal<number | null>(null);
  readonly partyLabelText = signal('');
  readonly partySearch = new FormControl('', { nonNullable: true });
  readonly partyHits = signal<PartyRecord[]>([]);
  readonly paymentDate = new FormControl(new Date().toISOString().slice(0, 10), { nonNullable: true });
  readonly amount = new FormControl('', { nonNullable: true });
  readonly method = signal<PaymentMethod>('CASH');
  readonly reference = new FormControl('', { nonNullable: true });
  readonly notes = new FormControl('', { nonNullable: true });
  readonly rows = signal<AllocationRow[]>([]);
  readonly errorMessage = signal('');
  readonly saving = signal(false);
  readonly dirty = signal(false);

  constructor() {
    effect(() => {
      const preferred = this.snapshot.preferences()?.defaultPaymentMethod;
      if (this.paymentId() || this.dirty() || this.appliedDefault || !preferred) {
        return;
      }
      if ((PAYMENT_METHODS as readonly string[]).includes(preferred)) {
        this.method.set(preferred as PaymentMethod);
        this.appliedDefault = true;
      }
    });
    const destroyRef = inject(DestroyRef);
    this.route.data.pipe(takeUntilDestroyed(destroyRef)).subscribe((data) => this.party.set(data['party']));
    this.route.paramMap.pipe(takeUntilDestroyed(destroyRef)).subscribe((params) => {
      this.party.set((this.route.snapshot.data['party'] as PartyKind) ?? this.party());
      const id = params.get('id');
      if (id) {
        this.loadPayment(Number(id));
      }
    });
    this.partySearch.valueChanges
      .pipe(
        debounceTime(250),
        distinctUntilChanged(),
        switchMap((term) =>
          term.trim().length === 0 || this.paymentId()
            ? of({ items: [] as PartyRecord[] })
            : this.parties.list(
                this.party() === 'customer' ? 'customers' : 'suppliers',
                term.trim(),
                'ACTIVE',
                0,
                this.party() === 'customer' ? 'customerName,asc' : 'supplierName,asc',
                8,
              ),
        ),
        takeUntilDestroyed(destroyRef),
      )
      .subscribe({
        next: (result) => this.partyHits.set(result.items),
        error: () => this.partyHits.set([]),
      });
  }

  title(): string {
    const noun = this.party() === 'customer' ? 'customer payment' : 'supplier payment';
    return this.paymentId() ? `Edit ${noun}` : `New ${noun}`;
  }

  partyName(party: PartyRecord): string {
    return this.party() === 'customer' ? (party.customerName ?? '') : (party.supplierName ?? '');
  }

  methodLabel(method: string): string {
    return method.replaceAll('_', ' ').toLowerCase().replace(/^\w/, (letter) => letter.toUpperCase());
  }

  appliedAmount(): string {
    const used = this.rows().reduce((sum, row) => sum + toScaled(row.amount), 0n);
    return fromScaled(used);
  }

  unallocated(): string {
    const used = this.rows().reduce((sum, row) => sum + toScaled(row.amount), 0n);
    return fromScaled(toScaled(this.amount.value) - used);
  }

  hasUnsavedChanges(): boolean {
    return this.dirty() && !this.saving();
  }

  markDirty(): void {
    this.dirty.set(true);
  }

  takeParty(event: Event): void {
    event.preventDefault();
    const party = this.partyHits()[0];
    if (party) {
      this.chooseParty(party);
    }
  }

  clearParty(): void {
    this.partyId.set(null);
    this.partyLabelText.set('');
    this.partySearch.setValue('');
    this.partyHits.set([]);
    this.rows.set([]);
    this.markDirty();
  }

  chooseParty(party: PartyRecord): void {
    this.partyId.set(party.id);
    this.partyLabelText.set(`${this.partyName(party)} · ${this.party() === 'customer' ? party.customerCode : party.supplierCode}`);
    this.partySearch.setValue(this.partyName(party), { emitEvent: false });
    this.partyHits.set([]);
    this.markDirty();
    this.loadInvoices([]);
  }

  onAmount(value: string): void {
    this.amount.setValue(value);
    this.markDirty();
  }

  onAllocate(index: number, value: string): void {
    this.rows.update((rows) => rows.map((row, position) => (position === index ? { ...row, amount: value } : row)));
    this.markDirty();
  }

  fillOldest(): void {
    const applied = allocateOldest(
      this.amount.value,
      this.rows().map((row) => String(row.invoice.openBalance)),
    );
    this.rows.update((rows) => rows.map((row, index) => ({ ...row, amount: applied[index] ?? '0.0000' })));
    this.markDirty();
  }

  save(): void {
    if (this.partyId() == null) {
      this.errorMessage.set(`Choose a ${this.party()}.`);
      return;
    }
    if (!this.paymentDate.value || compareMoney(this.amount.value, 0) <= 0) {
      this.errorMessage.set('Enter a payment date and an amount greater than zero.');
      return;
    }
    if (compareMoney(this.unallocated(), 0) < 0) {
      this.errorMessage.set('Allocated amounts cannot exceed the payment.');
      return;
    }
    const allocations = this.rows()
      .filter((row) => compareMoney(row.amount, 0) > 0)
      .map((row) => ({ invoiceId: row.invoice.invoiceId, amount: row.amount }));
    const body = {
      paymentDate: this.paymentDate.value,
      amount: this.amount.value,
      paymentMethod: this.method(),
      referenceNumber: blankToNull(this.reference.value),
      notes: blankToNull(this.notes.value),
      allocations,
      ...(this.party() === 'customer' ? { customerId: this.partyId()! } : { supplierId: this.partyId()! }),
      ...(this.version() == null ? {} : { version: this.version()! }),
    };
    this.saving.set(true);
    this.errorMessage.set('');
    const request = this.paymentId()
      ? this.api.updatePayment(this.party(), this.paymentId()!, body)
      : this.api.createPayment(this.party(), body);
    request.subscribe({
      next: (payment) => {
        this.dirty.set(false);
        this.saving.set(false);
        void this.router.navigate(['/payments', this.party(), payment.id]);
      },
      error: (error: unknown) => {
        this.saving.set(false);
        this.errorMessage.set(explainError(error));
      },
    });
  }

  private loadPayment(id: number): void {
    this.api.payment(this.party(), id).subscribe({
      next: (payment) => {
        this.paymentId.set(payment.id);
        this.version.set(payment.version);
        this.partyId.set(payment.partyId);
        this.partyLabelText.set(`${payment.partyName} · ${payment.partyCode}`);
        this.partySearch.setValue(payment.partyName, { emitEvent: false });
        this.paymentDate.setValue(payment.paymentDate);
        this.amount.setValue(String(payment.amount));
        this.method.set(payment.paymentMethod);
        this.reference.setValue(payment.referenceNumber ?? '');
        this.notes.setValue(payment.notes ?? '');
        const posted = payment.allocations.filter((row) => row.status === 'POSTED');
        this.loadInvoices(posted.map((row) => ({ invoiceId: row.invoiceId, amount: String(row.amount) })));
        this.dirty.set(false);
      },
      error: (error: unknown) => this.errorMessage.set(explainError(error)),
    });
  }

  private loadInvoices(existing: { invoiceId: number; amount: string }[]): void {
    const partyId = this.partyId();
    if (partyId == null) {
      return;
    }
    this.api.openInvoices(this.party(), partyId).subscribe({
      next: (invoices) => {
        const known = new Map(invoices.map((invoice) => [invoice.invoiceId, invoice]));
        for (const row of existing) {
          if (!known.has(row.invoiceId)) {
            known.set(row.invoiceId, {
              invoiceId: row.invoiceId,
              documentNumber: `Invoice ${row.invoiceId}`,
              documentDate: this.paymentDate.value,
              grandTotal: row.amount,
              paidAmount: 0,
              openBalance: row.amount,
              dueDate: null,
              overdue: false,
              dueDateSupported: false,
            });
          }
        }
        this.rows.set(
          [...known.values()].map((invoice) => ({
            invoice,
            amount: existing.find((row) => row.invoiceId === invoice.invoiceId)?.amount ?? '0.0000',
          })),
        );
      },
      error: (error: unknown) => this.errorMessage.set(explainError(error)),
    });
  }
}

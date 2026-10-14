import { ChangeDetectionStrategy, Component, DestroyRef, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { UnsavedForm } from '../../core/guards/unsaved-changes.guard';
import { SystemSnapshot } from '../../core/services/system-snapshot.service';
import { readApiError } from '../../core/utils/http-error.util';
import { blankToNull } from '../master/master-format';
import { PageHeader } from '../../shared/components/page-header/page-header';
import { FinanceNav } from './finance-nav';
import { FinanceApi } from './finance-api.service';
import { compareMoney } from './finance-money';
import { EXPENSE_CATEGORIES, ExpenseCategory, PAYMENT_METHODS, PaymentMethod } from './finance.model';

@Component({
  selector: 'app-expense-form-page',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    RouterLink,
    PageHeader,
    FinanceNav,
  ],
  templateUrl: './expense-form-page.html',
  styleUrls: ['./finance-screen.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ExpenseFormPage implements UnsavedForm {
  private readonly api = inject(FinanceApi);
  private readonly snapshot = inject(SystemSnapshot);
  private appliedDefault = false;
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly categories = EXPENSE_CATEGORIES;
  readonly methods = PAYMENT_METHODS;
  readonly expenseId = signal<number | null>(null);
  readonly version = signal<number | null>(null);
  readonly expenseDate = new FormControl(new Date().toISOString().slice(0, 10), { nonNullable: true });
  readonly category = signal<ExpenseCategory>('OFFICE');
  readonly description = new FormControl('', { nonNullable: true });
  readonly amount = new FormControl('', { nonNullable: true });
  readonly method = signal<PaymentMethod>('CASH');
  readonly reference = new FormControl('', { nonNullable: true });
  readonly notes = new FormControl('', { nonNullable: true });
  readonly errorMessage = signal('');
  readonly saving = signal(false);
  readonly dirty = signal(false);

  constructor() {
    effect(() => {
      const preferred = this.snapshot.preferences()?.defaultPaymentMethod;
      if (this.expenseId() || this.dirty() || this.appliedDefault || !preferred) {
        return;
      }
      if ((PAYMENT_METHODS as readonly string[]).includes(preferred)) {
        this.method.set(preferred as PaymentMethod);
        this.appliedDefault = true;
      }
    });
    this.route.paramMap.pipe(takeUntilDestroyed(inject(DestroyRef))).subscribe((params) => {
      const id = params.get('id');
      if (!id) {
        return;
      }
      this.api.expense(Number(id)).subscribe({
        next: (expense) => {
          this.expenseId.set(expense.id);
          this.version.set(expense.version);
          this.expenseDate.setValue(expense.expenseDate);
          this.category.set(expense.category);
          this.description.setValue(expense.description);
          this.amount.setValue(String(expense.amount));
          this.method.set(expense.paymentMethod);
          this.reference.setValue(expense.referenceNumber ?? '');
          this.notes.setValue(expense.notes ?? '');
          this.dirty.set(false);
        },
        error: (error: unknown) => this.errorMessage.set(readApiError(error)),
      });
    });
  }

  hasUnsavedChanges(): boolean {
    return this.dirty() && !this.saving();
  }

  markDirty(): void {
    this.dirty.set(true);
  }

  save(): void {
    if (!this.description.value.trim() || !this.expenseDate.value || compareMoney(this.amount.value, 0) <= 0) {
      this.errorMessage.set('Enter a date, description, and an amount greater than zero.');
      return;
    }
    const body = {
      expenseDate: this.expenseDate.value,
      category: this.category(),
      description: this.description.value.trim(),
      amount: this.amount.value,
      paymentMethod: this.method(),
      referenceNumber: blankToNull(this.reference.value),
      notes: blankToNull(this.notes.value),
      ...(this.version() == null ? {} : { version: this.version()! }),
    };
    this.saving.set(true);
    this.errorMessage.set('');
    const request = this.expenseId()
      ? this.api.updateExpense(this.expenseId()!, body)
      : this.api.createExpense(body);
    request.subscribe({
      next: (expense) => {
        this.dirty.set(false);
        this.saving.set(false);
        void this.router.navigate(['/expenses', expense.id]);
      },
      error: (error: unknown) => {
        this.saving.set(false);
        this.errorMessage.set(readApiError(error));
      },
    });
  }
}

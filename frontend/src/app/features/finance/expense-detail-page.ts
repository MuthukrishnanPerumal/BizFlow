import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { ConfirmationService } from '../../core/services/confirmation.service';
import { readApiError } from '../../core/utils/http-error.util';
import { formatDate, formatMoney, formatWhen } from '../master/master-format';
import { ErrorState } from '../../shared/components/error-state/error-state';
import { PageHeader } from '../../shared/components/page-header/page-header';
import { SkeletonLoader } from '../../shared/components/skeleton-loader/skeleton-loader';
import { StatusBadge } from '../../shared/components/status-badge/status-badge';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { FinanceApi } from './finance-api.service';
import { FinanceNav } from './finance-nav';
import { ExpenseDetail } from './finance.model';

@Component({
  selector: 'app-expense-detail-page',
  imports: [
    MatButtonModule,
    RouterLink,
    PageHeader,
    ErrorState,
    SkeletonLoader,
    StatusBadge,
    HasPermissionDirective,
    FinanceNav,
  ],
  templateUrl: './expense-detail-page.html',
  styleUrls: ['../master/master-table.scss', '../../shared/components/data-table/data-table.scss', './finance-screen.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ExpenseDetailPage {
  private readonly api = inject(FinanceApi);
  private readonly route = inject(ActivatedRoute);
  private readonly confirmation = inject(ConfirmationService);

  readonly money = formatMoney;
  readonly date = formatDate;
  readonly when = formatWhen;
  readonly loading = signal(true);
  readonly failed = signal(false);
  readonly errorMessage = signal('');
  readonly expense = signal<ExpenseDetail | null>(null);
  readonly expenseId = signal(0);

  constructor() {
    this.route.paramMap.pipe(takeUntilDestroyed(inject(DestroyRef))).subscribe((params) => {
      const id = Number(params.get('id'));
      this.expenseId.set(id);
      if (id) {
        this.load(id);
      }
    });
  }

  cancel(): void {
    const expense = this.expense();
    if (!expense) {
      return;
    }
    this.confirmation
      .confirm({
        title: 'Cancel expense',
        message: 'This reverses the ledger entry for the expense.',
        confirmLabel: 'Cancel expense',
        cancelLabel: 'Keep expense',
        destructive: true,
      })
      .subscribe((confirmed) => {
        if (!confirmed) {
          return;
        }
        this.api.cancelExpense(expense.id, expense.version).subscribe({
          next: (updated) => this.expense.set(updated),
          error: (error: unknown) => this.errorMessage.set(readApiError(error)),
        });
      });
  }

  load(id: number): void {
    this.loading.set(true);
    this.failed.set(false);
    this.api.expense(id).subscribe({
      next: (expense) => {
        this.expense.set(expense);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.loading.set(false);
        this.failed.set(true);
        this.errorMessage.set(readApiError(error));
      },
    });
  }
}

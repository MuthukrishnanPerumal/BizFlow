import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { RouterLink } from '@angular/router';

import { readApiError } from '../../core/utils/http-error.util';
import { formatDate, formatMoney } from '../master/master-format';
import { IsoDateField } from '../../shared/components/iso-date-field/iso-date-field';
import { EmptyState } from '../../shared/components/empty-state/empty-state';
import { ErrorState } from '../../shared/components/error-state/error-state';
import { FilterPanel } from '../../shared/components/filter-panel/filter-panel';
import { PageHeader } from '../../shared/components/page-header/page-header';
import { Pagination } from '../../shared/components/pagination/pagination';
import { SearchBar } from '../../shared/components/search-bar/search-bar';
import { SkeletonLoader } from '../../shared/components/skeleton-loader/skeleton-loader';
import { StatusBadge } from '../../shared/components/status-badge/status-badge';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { FinanceApi } from './finance-api.service';
import { FinanceNav } from './finance-nav';
import { EXPENSE_CATEGORIES, ExpenseReport, PAYMENT_METHODS } from './finance.model';

@Component({
  selector: 'app-expense-list-page',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    RouterLink,
    PageHeader,
    IsoDateField,
    SearchBar,
    FilterPanel,
    Pagination,
    EmptyState,
    ErrorState,
    SkeletonLoader,
    StatusBadge,
    HasPermissionDirective,
    FinanceNav,
  ],
  templateUrl: './expense-list-page.html',
  styleUrls: ['../master/master-table.scss', '../../shared/components/data-table/data-table.scss', './finance-screen.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ExpenseListPage {
  private readonly api = inject(FinanceApi);

  readonly money = formatMoney;
  readonly date = formatDate;
  readonly categories = EXPENSE_CATEGORIES;
  readonly methods = PAYMENT_METHODS;
  readonly dateFrom = new FormControl('', { nonNullable: true });
  readonly dateTo = new FormControl('', { nonNullable: true });
  readonly minAmount = new FormControl('', { nonNullable: true });
  readonly maxAmount = new FormControl('', { nonNullable: true });
  readonly loading = signal(true);
  readonly failed = signal(false);
  readonly errorMessage = signal('');
  readonly report = signal<ExpenseReport | null>(null);
  readonly page = signal(0);
  readonly search = signal('');
  readonly status = signal('');
  readonly category = signal('');
  readonly method = signal('');
  readonly sort = signal('expenseDate,desc');
  readonly pageSize = 20;

  constructor() {
    this.load();
  }

  onSearch(value: string): void {
    this.search.set(value);
    this.page.set(0);
    this.load();
  }

  applyFilters(): void {
    this.page.set(0);
    this.load();
  }

  onPage(page: number): void {
    this.page.set(page);
    this.load();
  }

  toggleSort(field: string): void {
    const [current, direction] = this.sort().split(',');
    this.sort.set(`${field},${current === field && direction === 'asc' ? 'desc' : 'asc'}`);
    this.load();
  }

  sortMark(field: string): string {
    const [current, direction] = this.sort().split(',');
    if (current !== field) {
      return 'unfold_more';
    }
    return direction === 'asc' ? 'arrow_upward' : 'arrow_downward';
  }

  load(): void {
    this.loading.set(true);
    this.failed.set(false);
    this.api
      .expenses({
        search: this.search(),
        status: this.status(),
        category: this.category(),
        paymentMethod: this.method(),
        dateFrom: this.dateFrom.value,
        dateTo: this.dateTo.value,
        minAmount: this.minAmount.value,
        maxAmount: this.maxAmount.value,
        page: String(this.page()),
        size: String(this.pageSize),
        sort: this.sort(),
      })
      .subscribe({
        next: (report) => {
          this.report.set(report);
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

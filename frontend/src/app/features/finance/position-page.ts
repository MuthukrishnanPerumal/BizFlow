import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { readApiError } from '../../core/utils/http-error.util';
import { formatMoney } from '../master/master-format';
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
import { compareMoney } from './finance-money';
import { PayableRow, PositionReport, ReceivableRow } from './finance.model';

@Component({
  selector: 'app-position-page',
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
  templateUrl: './position-page.html',
  styleUrls: ['../master/master-table.scss', '../../shared/components/data-table/data-table.scss', './finance-screen.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PositionPage {
  private readonly api = inject(FinanceApi);
  private readonly route = inject(ActivatedRoute);

  readonly money = formatMoney;
  readonly kind = signal<'receivable' | 'payable'>('receivable');
  readonly dateFrom = new FormControl('', { nonNullable: true });
  readonly dateTo = new FormControl('', { nonNullable: true });
  readonly minAmount = new FormControl('', { nonNullable: true });
  readonly maxAmount = new FormControl('', { nonNullable: true });
  readonly loading = signal(true);
  readonly failed = signal(false);
  readonly errorMessage = signal('');
  readonly report = signal<PositionReport<ReceivableRow | PayableRow> | null>(null);
  readonly page = signal(0);
  readonly search = signal('');
  readonly status = signal('');
  readonly sort = signal('outstanding,desc');
  readonly pageSize = 20;

  constructor() {
    this.kind.set(this.route.snapshot.data['kind']);
    this.load();
  }

  title(): string {
    return this.kind() === 'receivable' ? 'Receivables' : 'Payables';
  }

  partyHeading(): string {
    return this.kind() === 'receivable' ? 'Customer' : 'Supplier';
  }

  documentHeading(): string {
    return this.kind() === 'receivable' ? 'Sales' : 'Purchases';
  }

  name(row: ReceivableRow | PayableRow): string {
    return 'customerName' in row ? row.customerName : row.supplierName;
  }

  code(row: ReceivableRow | PayableRow): string {
    return 'customerCode' in row ? row.customerCode : row.supplierCode;
  }

  documents(row: ReceivableRow | PayableRow): string | number {
    return 'totalSales' in row ? row.totalSales : row.totalPurchases;
  }

  paymentLink(row: ReceivableRow | PayableRow): string[] {
    return this.kind() === 'receivable' ? ['/payments/customer'] : ['/payments/supplier'];
  }

  paymentQuery(row: ReceivableRow | PayableRow): Record<string, number> {
    return this.kind() === 'receivable'
      ? { customerId: (row as ReceivableRow).customerId }
      : { supplierId: (row as PayableRow).supplierId };
  }

  balanceClass(value: string | number): string {
    const compared = compareMoney(value, 0);
    if (compared > 0) {
      return 'due';
    }
    if (compared < 0) {
      return 'credit';
    }
    return 'clear';
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

  overdueCount(): number {
    const report = this.report();
    if (!report) {
      return 0;
    }
    return report.overdueCustomers ?? report.overdueSuppliers ?? 0;
  }

  documentTotal(): string | number {
    const report = this.report();
    if (!report) {
      return 0;
    }
    return report.totalSales ?? report.totalPurchases ?? 0;
  }

  load(): void {
    this.loading.set(true);
    this.failed.set(false);
    const query = {
      search: this.search(),
      status: this.status(),
      dateFrom: this.dateFrom.value,
      dateTo: this.dateTo.value,
      minAmount: this.minAmount.value,
      maxAmount: this.maxAmount.value,
      page: String(this.page()),
      size: String(this.pageSize),
      sort: this.sort(),
    };
    const next = (report: PositionReport<ReceivableRow | PayableRow>): void => {
      this.report.set(report);
      this.loading.set(false);
    };
    const error = (failure: unknown): void => {
      this.loading.set(false);
      this.failed.set(true);
      this.errorMessage.set(readApiError(failure));
    };
    if (this.kind() === 'receivable') {
      this.api.receivables(query).subscribe({ next, error });
    } else {
      this.api.payables(query).subscribe({ next, error });
    }
  }
}

import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatSelectModule } from '@angular/material/select';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { readApiError } from '../../core/utils/http-error.util';
import { EmptyState } from '../../shared/components/empty-state/empty-state';
import { ErrorState } from '../../shared/components/error-state/error-state';
import { PageHeader } from '../../shared/components/page-header/page-header';
import { Pagination } from '../../shared/components/pagination/pagination';
import { SearchBar } from '../../shared/components/search-bar/search-bar';
import { SkeletonLoader } from '../../shared/components/skeleton-loader/skeleton-loader';
import { StatusBadge } from '../../shared/components/status-badge/status-badge';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { PeriodFilter } from '../reporting/period-filter';
import { ReportingApi } from '../reporting/reporting-api.service';
import { formatMoney, formatPeriod, formatQty } from '../reporting/reporting-format';
import { DocumentScope, ReportPreset, ReportRow, StatementView } from '../reporting/reporting.model';

type ReportKind = 'sales' | 'purchases' | 'inventory' | 'profit';

interface ReportColumn {
  id: keyof ReportRow;
  header: string;
  align?: 'end';
  kind: 'text' | 'money' | 'qty' | 'status' | 'date';
}

@Component({
  selector: 'app-report-page',
  imports: [
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatMenuModule,
    MatSelectModule,
    RouterLink,
    PageHeader,
    SearchBar,
    PeriodFilter,
    Pagination,
    EmptyState,
    ErrorState,
    SkeletonLoader,
    StatusBadge,
    HasPermissionDirective,
  ],
  templateUrl: './report-page.html',
  styleUrls: ['../reporting/reporting-screen.scss', '../master/master-table.scss', '../../shared/components/data-table/data-table.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReportPage {
  private readonly api = inject(ReportingApi);
  private readonly route = inject(ActivatedRoute);

  readonly money = formatMoney;
  readonly qty = formatQty;
  readonly kind = signal<ReportKind>('sales');
  readonly preset = signal<ReportPreset>('THIS_MONTH');
  readonly from = signal('');
  readonly to = signal('');
  readonly search = signal('');
  readonly scope = signal<DocumentScope>('POSTED');
  readonly searchToken = signal(0);
  readonly page = signal(0);
  readonly sort = signal('date,desc');
  readonly loading = signal(true);
  readonly exporting = signal(false);
  readonly failed = signal(false);
  readonly errorMessage = signal('');
  readonly rows = signal<ReportRow[]>([]);
  readonly totals = signal<ReportRow | null>(null);
  readonly statement = signal<StatementView | null>(null);
  readonly total = signal(0);
  readonly periodLabel = signal('');
  readonly pageSize = 20;

  constructor() {
    this.route.data.pipe(takeUntilDestroyed(inject(DestroyRef))).subscribe((data) => {
      const kind = data['report'];
      this.kind.set(kind === 'purchases' || kind === 'inventory' || kind === 'profit' ? kind : 'sales');
      this.page.set(0);
      this.search.set('');
      this.searchToken.update((value) => value + 1);
      this.scope.set('POSTED');
      this.sort.set(this.defaultSort());
      this.load();
    });
  }

  title(): string {
    return { sales: 'Sales report', purchases: 'Purchase report', inventory: 'Inventory report', profit: 'Profit report' }[this.kind()];
  }

  highlights(): { label: string; value: string; emphasis: boolean }[] {
    const footer = this.totals();
    if (!footer) {
      return [];
    }
    const wanted =
      this.kind() === 'profit'
        ? ['revenue', 'grossProfit', 'netProfit']
        : this.kind() === 'inventory'
          ? ['closingStock', 'stockValue']
          : ['total', 'balance', 'paid'];
    const labels: Record<string, string> = {
      total: 'Total',
      balance: 'Outstanding',
      paid: 'Paid',
      revenue: 'Revenue',
      grossProfit: 'Gross profit',
      netProfit: 'Profit',
      closingStock: 'Quantity',
      stockValue: 'Stock value',
    };
    return this.columns()
      .filter((column) => wanted.includes(column.id))
      .map((column) => ({
        label: labels[column.id] ?? column.header,
        value: this.cell(footer, column),
        emphasis: ['total', 'netProfit', 'stockValue', 'balance'].includes(column.id),
      }));
  }

  subtitle(): string {
    const period = this.periodLabel();
    const detail = {
      sales: 'Posted invoices with current paid and balance amounts.',
      purchases: 'Posted bills with current paid and balance amounts.',
      inventory: 'Opening, movement, and closing stock. Adjustments include transfers.',
      profit: 'Revenue, the cost of goods sold, and the profit left after expenses.',
    }[this.kind()];
    return period ? `${detail} ${period}.` : detail;
  }

  columns(): readonly ReportColumn[] {
    if (this.kind() === 'inventory') {
      return [
        { id: 'product', header: 'Product', kind: 'text' },
        { id: 'openingStock', header: 'Opening', align: 'end', kind: 'qty' },
        { id: 'purchase', header: 'Purchase', align: 'end', kind: 'qty' },
        { id: 'purchaseReturn', header: 'Purchase return', align: 'end', kind: 'qty' },
        { id: 'sales', header: 'Sales', align: 'end', kind: 'qty' },
        { id: 'salesReturn', header: 'Sales return', align: 'end', kind: 'qty' },
        { id: 'adjustment', header: 'Adjustment', align: 'end', kind: 'qty' },
        { id: 'closingStock', header: 'Closing', align: 'end', kind: 'qty' },
        { id: 'stockValue', header: 'Stock value', align: 'end', kind: 'money' },
      ];
    }
    if (this.kind() === 'profit') {
      return [
        { id: 'period', header: 'Period', kind: 'text' },
        { id: 'revenue', header: 'Revenue', align: 'end', kind: 'money' },
        { id: 'costOfGoodsSold', header: 'COGS', align: 'end', kind: 'money' },
        { id: 'grossProfit', header: 'Gross profit', align: 'end', kind: 'money' },
        { id: 'expenses', header: 'Expenses', align: 'end', kind: 'money' },
        { id: 'netProfit', header: 'Net profit', align: 'end', kind: 'money' },
      ];
    }
    const party: ReportColumn = this.kind() === 'purchases'
      ? { id: 'supplier', header: 'Supplier', kind: 'text' }
      : { id: 'customer', header: 'Customer', kind: 'text' };
    return [
      { id: 'invoice', header: 'Invoice', kind: 'text' },
      { id: 'date', header: 'Date', kind: 'date' },
      party,
      { id: 'subtotal', header: 'Subtotal', align: 'end', kind: 'money' },
      { id: 'discount', header: 'Discount', align: 'end', kind: 'money' },
      { id: 'tax', header: 'Tax', align: 'end', kind: 'money' },
      { id: 'total', header: 'Total', align: 'end', kind: 'money' },
      { id: 'paid', header: 'Paid', align: 'end', kind: 'money' },
      { id: 'balance', header: 'Balance', align: 'end', kind: 'money' },
      { id: 'status', header: 'Status', kind: 'status' },
    ];
  }

  showsScope(): boolean {
    return this.kind() === 'sales' || this.kind() === 'purchases';
  }

  searchPlaceholder(): string {
    return {
      sales: 'Search invoice, customer, or phone',
      purchases: 'Search invoice, supplier, or phone',
      inventory: 'Search product, code, barcode, or category',
      profit: 'Search a period',
    }[this.kind()];
  }

  onPreset(preset: ReportPreset): void {
    this.preset.set(preset);
    this.page.set(0);
    if (preset !== 'CUSTOM' || (this.from() && this.to())) {
      this.load();
    }
  }

  onFrom(value: string): void {
    this.from.set(value);
    this.page.set(0);
    if (this.to()) {
      this.load();
    }
  }

  onTo(value: string): void {
    this.to.set(value);
    this.page.set(0);
    if (this.from()) {
      this.load();
    }
  }

  onSearch(value: string): void {
    this.search.set(value);
    this.page.set(0);
    this.load();
  }

  onScope(value: DocumentScope): void {
    this.scope.set(value);
    this.page.set(0);
    this.load();
  }

  onPage(page: number): void {
    this.page.set(page);
    this.load();
  }

  toggleSort(field: string): void {
    const [current, direction] = this.sort().split(',');
    const next = current === field && direction === 'asc' ? 'desc' : 'asc';
    this.sort.set(`${field},${next}`);
    this.page.set(0);
    this.load();
  }

  sortMark(field: string): string {
    const [current, direction] = this.sort().split(',');
    if (current !== field) {
      return 'unfold_more';
    }
    return direction === 'asc' ? 'arrow_upward' : 'arrow_downward';
  }

  reset(): void {
    this.preset.set('THIS_MONTH');
    this.from.set('');
    this.to.set('');
    this.search.set('');
    this.searchToken.update((value) => value + 1);
    this.scope.set('POSTED');
    this.page.set(0);
    this.sort.set(this.defaultSort());
    this.load();
  }

  printQuery(): Record<string, string> {
    return {
      preset: this.preset(),
      from: this.from(),
      to: this.to(),
      search: this.search(),
      sort: this.sort(),
    };
  }

  exportReport(format: 'csv' | 'xlsx' | 'pdf'): void {
    this.exporting.set(true);
    this.api
      .export(this.kind(), { preset: this.preset(), from: this.from(), to: this.to() }, {
        search: this.search(),
        scope: this.scope(),
        sort: this.sort(),
      }, format)
      .subscribe({
        next: (blob) => {
          const url = URL.createObjectURL(blob);
          const anchor = document.createElement('a');
          anchor.href = url;
          anchor.download = `${this.kind()}-report.${format}`;
          anchor.click();
          URL.revokeObjectURL(url);
          this.exporting.set(false);
        },
        error: (error: unknown) => {
          this.errorMessage.set(readApiError(error));
          this.failed.set(true);
          this.exporting.set(false);
        },
      });
  }

  cell(row: ReportRow, column: ReportColumn): string {
    const value = row[column.id];
    if (column.kind === 'money') {
      return formatMoney(typeof value === 'number' ? value : null);
    }
    if (column.kind === 'qty') {
      return formatQty(typeof value === 'number' ? value : null);
    }
    if (value === null || value === undefined || value === '') {
      return '—';
    }
    return String(value);
  }

  load(): void {
    if (this.preset() === 'CUSTOM' && (!this.from() || !this.to())) {
      this.loading.set(false);
      this.rows.set([]);
      this.total.set(0);
      return;
    }
    this.loading.set(true);
    this.failed.set(false);
    this.api
      .report(this.kind(), { preset: this.preset(), from: this.from(), to: this.to() }, {
        search: this.search(),
        scope: this.scope(),
        page: this.page(),
        size: this.pageSize,
        sort: this.sort(),
      })
      .subscribe({
        next: (result) => {
          this.rows.set(result.items);
          this.total.set(result.totalElements);
          this.periodLabel.set(formatPeriod(result.period.from, result.period.to));
          this.statement.set(result.statement ?? null);
          this.totals.set(this.footer(result.totals, result.statement));
          this.loading.set(false);
        },
        error: (error: unknown) => {
          this.failed.set(true);
          this.errorMessage.set(readApiError(error));
          this.loading.set(false);
        },
      });
  }

  private footer(
    totals: Record<string, number> | undefined,
    statement: StatementView | null | undefined,
  ): ReportRow | null {
    if (this.kind() === 'profit' && statement) {
      return {
        revenue: statement.revenue,
        costOfGoodsSold: statement.costOfGoodsSold,
        grossProfit: statement.grossProfit,
        expenses: statement.expenses,
        netProfit: statement.netProfit,
      };
    }
    return totals ? (totals as ReportRow) : null;
  }

  private defaultSort(): string {
    if (this.kind() === 'inventory') {
      return 'product,asc';
    }
    if (this.kind() === 'profit') {
      return 'period,asc';
    }
    return 'date,desc';
  }
}

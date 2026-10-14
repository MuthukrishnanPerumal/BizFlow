import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
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
import { InventoryApi } from './inventory-api.service';
import { formatQty, label } from './inventory-format';
import { InventoryNav } from './inventory-nav';
import { LEDGER_TYPES, LedgerEntry, StockLocation } from './inventory.model';

@Component({
  selector: 'app-stock-ledger-page',
  imports: [
    ReactiveFormsModule,
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
    InventoryNav,
  ],
  templateUrl: './stock-ledger-page.html',
  styleUrls: ['../master/master-table.scss', '../../shared/components/data-table/data-table.scss', './inventory-screen.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StockLedgerPage {
  private readonly api = inject(InventoryApi);

  readonly types = LEDGER_TYPES;
  readonly money = formatMoney;
  readonly qty = formatQty;
  readonly date = formatDate;
  readonly label = label;
  readonly dateFrom = new FormControl('', { nonNullable: true });
  readonly dateTo = new FormControl('', { nonNullable: true });
  readonly loading = signal(true);
  readonly failed = signal(false);
  readonly errorMessage = signal('');
  readonly rows = signal<LedgerEntry[]>([]);
  readonly locations = signal<StockLocation[]>([]);
  readonly page = signal(0);
  readonly total = signal(0);
  readonly search = signal('');
  readonly transactionType = signal('');
  readonly locationId = signal<number | null>(null);
  readonly sort = signal('date,desc');
  readonly pageSize = 20;

  constructor() {
    this.api.locations().subscribe({
      next: (locations) => this.locations.set(locations),
      error: () => this.locations.set([]),
    });
    this.load();
  }

  onSearch(value: string): void {
    this.search.set(value);
    this.page.set(0);
    this.load();
  }

  onType(value: string): void {
    this.transactionType.set(value);
    this.page.set(0);
    this.load();
  }

  onLocation(value: string): void {
    this.locationId.set(value ? Number(value) : null);
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
    const next = current === field && direction === 'desc' ? 'asc' : 'desc';
    this.sort.set(`${field},${next}`);
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
      .ledger({
        search: this.search(),
        productId: null,
        locationId: this.locationId(),
        transactionType: this.transactionType(),
        dateFrom: this.dateFrom.value,
        dateTo: this.dateTo.value,
        page: this.page(),
        sort: this.sort(),
      })
      .subscribe({
        next: (result) => {
          this.rows.set(result.items);
          this.total.set(result.totalElements);
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

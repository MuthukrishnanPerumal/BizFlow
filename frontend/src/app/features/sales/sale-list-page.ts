import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatMenuModule } from '@angular/material/menu';
import { MatSelectModule } from '@angular/material/select';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged, of, switchMap } from 'rxjs';

import { PartyRecord } from '../../core/models/master.model';
import { readApiError } from '../../core/utils/http-error.util';
import { formatDate, formatMoney } from '../master/master-format';
import { PartyApi } from '../master/party-api.service';
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
import { collectionStatus } from '../../shared/utils/collection-status';
import { SaleApi } from './sale-api.service';
import { SaleSummary } from './sale.model';

@Component({
  selector: 'app-sale-list-page',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatMenuModule,
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
  ],
  templateUrl: './sale-list-page.html',
  styleUrls: ['../master/master-table.scss', '../../shared/components/data-table/data-table.scss', './sale-screen.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SaleListPage {
  private readonly api = inject(SaleApi);
  private readonly parties = inject(PartyApi);

  readonly money = formatMoney;
  readonly date = formatDate;
  readonly payStatus = collectionStatus;
  readonly invoice = new FormControl('', { nonNullable: true });
  readonly dateFrom = new FormControl('', { nonNullable: true });
  readonly dateTo = new FormControl('', { nonNullable: true });
  readonly customerSearch = new FormControl('', { nonNullable: true });
  readonly customerHits = signal<PartyRecord[]>([]);
  readonly loading = signal(true);
  readonly failed = signal(false);
  readonly errorMessage = signal('');
  readonly rows = signal<SaleSummary[]>([]);
  readonly page = signal(0);
  readonly total = signal(0);
  readonly search = signal('');
  readonly status = signal('');
  readonly paymentType = signal('');
  readonly customerId = signal<number | null>(null);
  readonly sort = signal('invoiceDate,desc');
  readonly pageSize = 20;

  constructor() {
    const requested = inject(ActivatedRoute).snapshot.queryParamMap.get('status')?.toUpperCase() ?? '';
    if (isDocumentStatus(requested)) {
      this.status.set(requested);
    }
    const destroyRef = inject(DestroyRef);
    this.customerSearch.valueChanges
      .pipe(
        debounceTime(250),
        distinctUntilChanged(),
        switchMap((term) =>
          term.trim().length === 0
            ? of({ items: [] as PartyRecord[] })
            : this.parties.list('customers', term.trim(), 'ACTIVE', 0, 'customerName,asc', 8),
        ),
        takeUntilDestroyed(destroyRef),
      )
      .subscribe({
        next: (result) => this.customerHits.set(result.items),
        error: () => this.customerHits.set([]),
      });
    this.load();
  }

  onSearch(value: string): void {
    this.search.set(value);
    this.page.set(0);
    this.load();
  }

  onStatus(value: string): void {
    this.status.set(value);
    this.page.set(0);
    this.load();
  }

  onPayment(value: string): void {
    this.paymentType.set(value);
    this.page.set(0);
    this.load();
  }

  applyFilters(): void {
    this.page.set(0);
    this.load();
  }

  chooseCustomer(customer: PartyRecord): void {
    this.customerId.set(customer.id);
    this.customerSearch.setValue(customer.customerName ?? '', { emitEvent: false });
    this.customerHits.set([]);
    this.applyFilters();
  }

  clearCustomer(): void {
    this.customerId.set(null);
    this.customerSearch.setValue('');
    this.applyFilters();
  }

  onPage(page: number): void {
    this.page.set(page);
    this.load();
  }

  toggleSort(field: string): void {
    const [current, direction] = this.sort().split(',');
    const next = current === field && direction === 'asc' ? 'desc' : 'asc';
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
      .list({
        search: this.search(),
        status: this.status(),
        paymentType: this.paymentType(),
        customerId: this.customerId(),
        invoiceNumber: this.invoice.value.trim(),
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

function isDocumentStatus(status: string): boolean {
  return (
    status === 'DRAFT' ||
    status === 'CONFIRMED' ||
    status === 'COMPLETED' ||
    status === 'PARTIALLY_RETURNED' ||
    status === 'RETURNED' ||
    status === 'CANCELLED'
  );
}

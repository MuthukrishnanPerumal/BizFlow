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
import { readApiError } from '../../core/utils/http-error.util';
import { PurchaseApi } from './purchase-api.service';
import { PurchaseSummary } from './purchase.model';

@Component({
  selector: 'app-purchase-list-page',
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
  templateUrl: './purchase-list-page.html',
  styleUrls: ['../master/master-table.scss', '../../shared/components/data-table/data-table.scss', './purchase-screen.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PurchaseListPage {
  private readonly api = inject(PurchaseApi);
  private readonly parties = inject(PartyApi);

  readonly money = formatMoney;
  readonly date = formatDate;
  readonly payStatus = collectionStatus;
  readonly invoice = new FormControl('', { nonNullable: true });
  readonly dateFrom = new FormControl('', { nonNullable: true });
  readonly dateTo = new FormControl('', { nonNullable: true });
  readonly supplierSearch = new FormControl('', { nonNullable: true });
  readonly supplierHits = signal<PartyRecord[]>([]);
  readonly loading = signal(true);
  readonly failed = signal(false);
  readonly errorMessage = signal('');
  readonly rows = signal<PurchaseSummary[]>([]);
  readonly page = signal(0);
  readonly total = signal(0);
  readonly search = signal('');
  readonly status = signal('');
  readonly supplierId = signal<number | null>(null);
  readonly sort = signal('purchaseDate,desc');
  readonly pageSize = 20;

  constructor() {
    const requested = inject(ActivatedRoute).snapshot.queryParamMap.get('status')?.toUpperCase() ?? '';
    if (isDocumentStatus(requested)) {
      this.status.set(requested);
    }
    const destroyRef = inject(DestroyRef);
    this.supplierSearch.valueChanges
      .pipe(
        debounceTime(250),
        distinctUntilChanged(),
        switchMap((term) =>
          term.trim().length === 0
            ? of({ items: [] as PartyRecord[] })
            : this.parties.list('suppliers', term.trim(), 'ACTIVE', 0, 'supplierName,asc', 8),
        ),
        takeUntilDestroyed(destroyRef),
      )
      .subscribe({
        next: (page) => this.supplierHits.set(page.items),
        error: () => this.supplierHits.set([]),
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

  applyFilters(): void {
    this.page.set(0);
    this.load();
  }

  chooseSupplier(supplier: PartyRecord): void {
    this.supplierId.set(supplier.id);
    this.supplierSearch.setValue(supplier.supplierName ?? '', { emitEvent: false });
    this.supplierHits.set([]);
    this.applyFilters();
  }

  clearSupplier(): void {
    this.supplierId.set(null);
    this.supplierSearch.setValue('');
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

  load(): void {
    this.loading.set(true);
    this.failed.set(false);
    this.api
      .list({
        search: this.search(),
        status: this.status(),
        supplierId: this.supplierId(),
        invoiceNumber: this.invoice.value.trim(),
        dateFrom: this.dateFrom.value,
        dateTo: this.dateTo.value,
        page: this.page(),
        sort: this.sort(),
      })
      .subscribe({
        next: (page) => {
          this.rows.set(page.items);
          this.total.set(page.totalElements);
          this.loading.set(false);
        },
        error: (error: unknown) => {
          this.failed.set(true);
          this.errorMessage.set(readApiError(error));
          this.loading.set(false);
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

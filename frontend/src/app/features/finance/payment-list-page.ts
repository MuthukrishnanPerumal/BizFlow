import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
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
import { FinanceApi } from './finance-api.service';
import { FinanceNav } from './finance-nav';
import { compareMoney } from './finance-money';
import { PAYMENT_METHODS, PartyKind, PaymentReport } from './finance.model';

@Component({
  selector: 'app-payment-list-page',
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
  templateUrl: './payment-list-page.html',
  styleUrls: ['../master/master-table.scss', '../../shared/components/data-table/data-table.scss', './finance-screen.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PaymentListPage {
  private readonly api = inject(FinanceApi);
  private readonly parties = inject(PartyApi);
  private readonly route = inject(ActivatedRoute);

  readonly money = formatMoney;
  readonly date = formatDate;
  readonly methods = PAYMENT_METHODS;
  readonly compare = compareMoney;
  readonly party = signal<PartyKind>('customer');
  readonly dateFrom = new FormControl('', { nonNullable: true });
  readonly dateTo = new FormControl('', { nonNullable: true });
  readonly minAmount = new FormControl('', { nonNullable: true });
  readonly maxAmount = new FormControl('', { nonNullable: true });
  readonly partySearch = new FormControl('', { nonNullable: true });
  readonly partyHits = signal<PartyRecord[]>([]);
  readonly loading = signal(true);
  readonly failed = signal(false);
  readonly errorMessage = signal('');
  readonly report = signal<PaymentReport | null>(null);
  readonly page = signal(0);
  readonly search = signal('');
  readonly status = signal('');
  readonly method = signal('');
  readonly partyId = signal<number | null>(null);
  readonly scope = signal('STANDALONE');
  readonly sort = signal('paymentDate,desc');
  readonly pageSize = 20;

  constructor() {
    const destroyRef = inject(DestroyRef);
    this.route.data.pipe(takeUntilDestroyed(destroyRef)).subscribe((data) => {
      const next = data['party'] as PartyKind;
      if (next !== this.party()) {
        this.party.set(next);
        this.partyId.set(null);
        this.partySearch.setValue('', { emitEvent: false });
        this.page.set(0);
      } else {
        this.party.set(next);
      }
      const preset = this.route.snapshot.queryParamMap.get(next === 'customer' ? 'customerId' : 'supplierId');
      if (preset) {
        this.partyId.set(Number(preset));
      }
      this.load();
    });
    this.partySearch.valueChanges
      .pipe(
        debounceTime(250),
        distinctUntilChanged(),
        switchMap((term) =>
          term.trim().length === 0
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
    return this.party() === 'customer' ? 'Customer payments' : 'Supplier payments';
  }

  partyLabel(): string {
    return this.party() === 'customer' ? 'Customer' : 'Supplier';
  }

  partyName(row: PartyRecord): string {
    return this.party() === 'customer' ? (row.customerName ?? '') : (row.supplierName ?? '');
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

  onMethod(value: string): void {
    this.method.set(value);
    this.page.set(0);
    this.load();
  }

  onScope(value: string): void {
    this.scope.set(value);
    this.page.set(0);
    this.load();
  }

  applyFilters(): void {
    this.page.set(0);
    this.load();
  }

  chooseParty(party: PartyRecord): void {
    this.partyId.set(party.id);
    this.partySearch.setValue(this.partyName(party), { emitEvent: false });
    this.partyHits.set([]);
    this.applyFilters();
  }

  clearParty(): void {
    this.partyId.set(null);
    this.partySearch.setValue('');
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

  load(): void {
    this.loading.set(true);
    this.failed.set(false);
    const partyKey = this.party() === 'customer' ? 'customerId' : 'supplierId';
    this.api
      .payments(this.party(), {
        search: this.search(),
        status: this.status(),
        paymentMethod: this.method(),
        [partyKey]: this.partyId() == null ? '' : String(this.partyId()),
        dateFrom: this.dateFrom.value,
        dateTo: this.dateTo.value,
        minAmount: this.minAmount.value,
        maxAmount: this.maxAmount.value,
        scope: this.scope(),
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

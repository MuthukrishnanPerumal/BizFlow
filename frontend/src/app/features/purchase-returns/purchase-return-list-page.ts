import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatMenuModule } from '@angular/material/menu';
import { RouterLink } from '@angular/router';

import { readApiError } from '../../core/utils/http-error.util';
import { formatDate, formatMoney } from '../master/master-format';
import { ReturnSummary } from '../purchases/purchase.model';
import { PurchaseApi } from '../purchases/purchase-api.service';
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

@Component({
  selector: 'app-purchase-return-list-page',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatMenuModule,
    HasPermissionDirective,
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
  ],
  templateUrl: './purchase-return-list-page.html',
  styleUrls: ['../master/master-table.scss', '../../shared/components/data-table/data-table.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PurchaseReturnListPage {
  private readonly api = inject(PurchaseApi);
  readonly money = formatMoney;
  readonly date = formatDate;
  readonly dateFrom = new FormControl('', { nonNullable: true });
  readonly dateTo = new FormControl('', { nonNullable: true });
  readonly loading = signal(true);
  readonly failed = signal(false);
  readonly errorMessage = signal('');
  readonly rows = signal<ReturnSummary[]>([]);
  readonly page = signal(0);
  readonly total = signal(0);
  readonly search = signal('');
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

  load(): void {
    this.loading.set(true);
    this.failed.set(false);
    this.api
      .returns({
        search: this.search(),
        supplierId: null,
        purchaseId: null,
        dateFrom: this.dateFrom.value,
        dateTo: this.dateTo.value,
        page: this.page(),
        sort: 'returnDate,desc',
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

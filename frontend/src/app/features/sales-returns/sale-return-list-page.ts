import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged } from 'rxjs';

import { readApiError } from '../../core/utils/http-error.util';
import { formatDate, formatMoney } from '../master/master-format';
import { SaleApi } from '../sales/sale-api.service';
import { ReturnSummary } from '../sales/sale.model';
import { IsoDateField } from '../../shared/components/iso-date-field/iso-date-field';
import { EmptyState } from '../../shared/components/empty-state/empty-state';
import { ErrorState } from '../../shared/components/error-state/error-state';
import { FilterPanel } from '../../shared/components/filter-panel/filter-panel';
import { PageHeader } from '../../shared/components/page-header/page-header';
import { Pagination } from '../../shared/components/pagination/pagination';
import { SearchBar } from '../../shared/components/search-bar/search-bar';
import { SkeletonLoader } from '../../shared/components/skeleton-loader/skeleton-loader';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';

@Component({
  selector: 'app-sale-return-list-page',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    RouterLink,
    PageHeader,
    IsoDateField,
    SearchBar,
    FilterPanel,
    Pagination,
    EmptyState,
    ErrorState,
    SkeletonLoader,
    HasPermissionDirective,
  ],
  templateUrl: './sale-return-list-page.html',
  styleUrls: ['../master/master-table.scss', '../../shared/components/data-table/data-table.scss', '../sales/sale-screen.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SaleReturnListPage {
  private readonly api = inject(SaleApi);
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
  readonly sort = signal('returnDate,desc');
  readonly pageSize = 20;

  constructor() {
    const destroyRef = inject(DestroyRef);
    this.dateFrom.valueChanges.pipe(debounceTime(200), distinctUntilChanged(), takeUntilDestroyed(destroyRef)).subscribe(() => this.apply());
    this.dateTo.valueChanges.pipe(debounceTime(200), distinctUntilChanged(), takeUntilDestroyed(destroyRef)).subscribe(() => this.apply());
    this.load();
  }

  onSearch(value: string): void {
    this.search.set(value);
    this.page.set(0);
    this.load();
  }

  apply(): void {
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
        customerId: null,
        saleId: null,
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

import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatMenuModule } from '@angular/material/menu';
import { MatSelectModule } from '@angular/material/select';
import { Router, RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';
import { MasterRef, ProductRecord } from '../../core/models/master.model';
import { ConfirmationService } from '../../core/services/confirmation.service';
import { NotificationService } from '../../core/services/notification.service';
import { readApiError } from '../../core/utils/http-error.util';
import { RecordLookup } from '../master/record-lookup';
import { FilterChip, SmartFilters } from '../master/smart-filters';
import { formatMoney } from '../master/master-format';
import { EmptyState } from '../../shared/components/empty-state/empty-state';
import { ErrorState } from '../../shared/components/error-state/error-state';
import { PageHeader } from '../../shared/components/page-header/page-header';
import { Pagination } from '../../shared/components/pagination/pagination';
import { SearchBar } from '../../shared/components/search-bar/search-bar';
import { SkeletonLoader } from '../../shared/components/skeleton-loader/skeleton-loader';
import { StatusBadge } from '../../shared/components/status-badge/status-badge';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { ProductApi } from './product-api.service';

@Component({
  selector: 'app-product-list-page',
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
    SearchBar,
    Pagination,
    EmptyState,
    ErrorState,
    SkeletonLoader,
    StatusBadge,
    HasPermissionDirective,
    RecordLookup,
    SmartFilters,
  ],
  templateUrl: './product-list-page.html',
  styleUrls: ['../master/master-table.scss', '../../shared/components/data-table/data-table.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProductListPage {
  private readonly api = inject(ProductApi);
  private readonly confirmation = inject(ConfirmationService);
  private readonly notifications = inject(NotificationService);
  private readonly router = inject(Router);
  readonly auth = inject(AuthService);
  readonly money = formatMoney;

  readonly barcode = new FormControl('', { nonNullable: true });
  readonly loading = signal(true);
  readonly failed = signal(false);
  readonly errorMessage = signal('');
  readonly rows = signal<ProductRecord[]>([]);
  readonly page = signal(0);
  readonly total = signal(0);
  readonly search = signal('');
  readonly status = signal('');
  readonly stockType = signal('');
  readonly category = signal<MasterRef | null>(null);
  readonly sort = signal('createdAt,desc');
  readonly searchToken = signal(0);
  readonly pageSize = 20;
  readonly chips = computed(() => productChips(this.status(), this.stockType(), this.category()?.name ?? ''));
  readonly filtered = computed(() => this.search().length > 0 || this.chips().length > 0);

  constructor() {
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

  onStockType(value: string): void {
    this.stockType.set(value);
    this.page.set(0);
    this.load();
  }

  onCategory(value: MasterRef | null): void {
    this.category.set(value);
    this.page.set(0);
    this.load();
  }

  removeChip(id: string): void {
    if (id === 'status') {
      this.status.set('');
    } else if (id === 'stock') {
      this.stockType.set('');
    } else if (id === 'category') {
      this.category.set(null);
    }
    this.page.set(0);
    this.load();
  }

  clearFilters(): void {
    this.status.set('');
    this.stockType.set('');
    this.category.set(null);
    this.search.set('');
    this.searchToken.update((value) => value + 1);
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
      return '';
    }
    return direction === 'asc' ? 'arrow_upward' : 'arrow_downward';
  }

  findBarcode(): void {
    const barcode = this.barcode.value.trim();
    if (!barcode) {
      return;
    }
    this.api.lookup(barcode).subscribe({
      next: (product) => void this.router.navigate(['/products', product.id]),
      error: (error: unknown) => this.notifications.error(readApiError(error)),
    });
  }

  remove(row: ProductRecord): void {
    this.confirmation
      .confirm({
        title: 'Delete product',
        message: `Delete ${row.name}? The product leaves the active catalog. Historical documents keep their own copy of the sale or purchase.`,
        confirmLabel: 'Delete',
        cancelLabel: 'Keep',
        destructive: true,
      })
      .subscribe((confirmed) => {
        if (!confirmed) {
          return;
        }
        this.api.delete(row.id).subscribe({
          next: () => {
            this.notifications.success('Product deleted');
            this.load();
          },
          error: (error: unknown) => this.notifications.error(readApiError(error)),
        });
      });
  }

  load(): void {
    this.loading.set(true);
    this.failed.set(false);
    this.api
      .list(this.search(), this.status(), this.stockType(), this.category()?.id ?? null, this.page(), this.sort())
      .subscribe({
        next: (result) => {
          this.rows.set(result.items);
          this.total.set(result.totalElements);
          this.loading.set(false);
        },
        error: (error: unknown) => {
          this.loading.set(false);
          this.failed.set(true);
          this.rows.set([]);
          this.errorMessage.set(readApiError(error));
        },
      });
  }
}

function productChips(status: string, stockType: string, category: string): FilterChip[] {
  const chips: FilterChip[] = [];
  if (status) {
    chips.push({ id: 'status', label: status === 'ACTIVE' ? 'Active' : 'Inactive' });
  }
  if (stockType) {
    const labels: Record<string, string> = { NONE: 'No stock tracking', BATCH: 'Batch', SERIAL: 'Serial', PICS: 'Piece' };
    chips.push({ id: 'stock', label: labels[stockType] ?? stockType });
  }
  if (category) {
    chips.push({ id: 'category', label: category });
  }
  return chips;
}

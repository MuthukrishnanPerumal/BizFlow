import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatSelectModule } from '@angular/material/select';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';
import { PartyRecord } from '../../core/models/master.model';
import { ConfirmationService } from '../../core/services/confirmation.service';
import { NotificationService } from '../../core/services/notification.service';
import { readApiError } from '../../core/utils/http-error.util';
import { EmptyState } from '../../shared/components/empty-state/empty-state';
import { ErrorState } from '../../shared/components/error-state/error-state';
import { PageHeader } from '../../shared/components/page-header/page-header';
import { Pagination } from '../../shared/components/pagination/pagination';
import { SearchBar } from '../../shared/components/search-bar/search-bar';
import { SkeletonLoader } from '../../shared/components/skeleton-loader/skeleton-loader';
import { StatusBadge } from '../../shared/components/status-badge/status-badge';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { PartyConfig, partyConfig } from './party.config';
import { FilterChip, SmartFilters } from './smart-filters';
import { PartyApi } from './party-api.service';

@Component({
  selector: 'app-party-list-page',
  imports: [
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
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
    SmartFilters,
  ],
  templateUrl: './party-list-page.html',
  styleUrls: ['./master-table.scss', '../../shared/components/data-table/data-table.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PartyListPage {
  private readonly api = inject(PartyApi);
  private readonly confirmation = inject(ConfirmationService);
  private readonly notifications = inject(NotificationService);
  private readonly route = inject(ActivatedRoute);
  readonly auth = inject(AuthService);

  readonly config: PartyConfig = partyConfig(this.route.snapshot.data['resource']);
  readonly loading = signal(true);
  readonly failed = signal(false);
  readonly errorMessage = signal('');
  readonly rows = signal<PartyRecord[]>([]);
  readonly page = signal(0);
  readonly total = signal(0);
  readonly search = signal('');
  readonly status = signal('');
  readonly sort = signal('createdAt,desc');
  readonly searchToken = signal(0);
  readonly pageSize = 20;
  readonly chips = computed<FilterChip[]>(() =>
    this.status() ? [{ id: 'status', label: this.status() === 'ACTIVE' ? 'Active' : 'Inactive' }] : [],
  );
  readonly filtered = computed(() => this.search().length > 0 || this.chips().length > 0);

  constructor() {
    this.load();
  }

  codeOf(row: PartyRecord): string {
    return row[this.config.codeField] ?? '';
  }

  nameOf(row: PartyRecord): string {
    return row[this.config.nameField] ?? '';
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

  removeChip(id: string): void {
    if (id === 'status') {
      this.status.set('');
      this.page.set(0);
      this.load();
    }
  }

  clearFilters(): void {
    this.status.set('');
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

  remove(row: PartyRecord): void {
    this.confirmation
      .confirm({
        title: `Delete ${this.config.singular}`,
        message: `Delete ${this.nameOf(row)}? Parties with business transactions should be set inactive instead.`,
        confirmLabel: 'Delete',
        cancelLabel: 'Keep',
        destructive: true,
      })
      .subscribe((confirmed) => {
        if (!confirmed) {
          return;
        }
        this.api.delete(this.config.resource, row.id).subscribe({
          next: () => {
            this.notifications.success('Record deleted');
            this.load();
          },
          error: (error: unknown) => this.notifications.error(readApiError(error)),
        });
      });
  }

  load(): void {
    this.loading.set(true);
    this.failed.set(false);
    this.api.list(this.config.resource, this.search(), this.status(), this.page(), this.sort()).subscribe({
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

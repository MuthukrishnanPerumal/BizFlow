import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatMenuModule } from '@angular/material/menu';
import { MatSelectModule } from '@angular/material/select';
import { RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';
import { UserRecord } from '../../core/models/identity.model';
import { ConfirmationService } from '../../core/services/confirmation.service';
import { NotificationService } from '../../core/services/notification.service';
import { readApiError } from '../../core/utils/http-error.util';
import { EmptyState } from '../../shared/components/empty-state/empty-state';
import { FilterPanel } from '../../shared/components/filter-panel/filter-panel';
import { PageHeader } from '../../shared/components/page-header/page-header';
import { Pagination } from '../../shared/components/pagination/pagination';
import { SearchBar } from '../../shared/components/search-bar/search-bar';
import { SkeletonLoader } from '../../shared/components/skeleton-loader/skeleton-loader';
import { StatusBadge } from '../../shared/components/status-badge/status-badge';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { UserApi } from './user-api.service';

@Component({
  selector: 'app-user-list-page',
  imports: [
    MatButtonModule,
    MatFormFieldModule,
    MatMenuModule,
    MatSelectModule,
    RouterLink,
    PageHeader,
    SearchBar,
    FilterPanel,
    Pagination,
    EmptyState,
    SkeletonLoader,
    StatusBadge,
    HasPermissionDirective,
  ],
  templateUrl: './user-list-page.html',
  styleUrls: ['./user-list-page.scss', '../../shared/components/data-table/data-table.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UserListPage {
  private readonly api = inject(UserApi);
  private readonly confirmation = inject(ConfirmationService);
  private readonly notifications = inject(NotificationService);
  readonly auth = inject(AuthService);

  readonly loading = signal(true);
  readonly users = signal<UserRecord[]>([]);
  readonly page = signal(0);
  readonly total = signal(0);
  readonly search = signal('');
  readonly status = signal('');
  readonly pageSize = 20;

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

  onPage(page: number): void {
    this.page.set(page);
    this.load();
  }

  roleNames(user: UserRecord): string {
    return user.roles.length === 0 ? 'No role' : user.roles.map((role) => role.name).join(', ');
  }

  remove(user: UserRecord): void {
    this.confirmation
      .confirm({
        title: 'Delete user',
        message: `Delete ${user.fullName}? They will no longer be able to sign in.`,
        confirmLabel: 'Delete user',
        cancelLabel: 'Keep user',
        destructive: true,
      })
      .subscribe((confirmed) => {
        if (!confirmed) {
          return;
        }
        this.api.delete(user.id).subscribe({
          next: () => {
            this.notifications.success('User deleted');
            this.load();
          },
          error: (error: unknown) => this.notifications.error(readApiError(error)),
        });
      });
  }

  private load(): void {
    this.loading.set(true);
    this.api.list(this.search(), this.status(), this.page()).subscribe({
      next: (result) => {
        this.users.set(result.items);
        this.total.set(result.totalElements);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.users.set([]);
      },
    });
  }
}

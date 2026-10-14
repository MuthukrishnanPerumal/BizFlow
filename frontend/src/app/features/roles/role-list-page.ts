import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatMenuModule } from '@angular/material/menu';
import { RouterLink } from '@angular/router';

import { RoleRecord } from '../../core/models/identity.model';
import { ConfirmationService } from '../../core/services/confirmation.service';
import { NotificationService } from '../../core/services/notification.service';
import { readApiError } from '../../core/utils/http-error.util';
import { EmptyState } from '../../shared/components/empty-state/empty-state';
import { PageHeader } from '../../shared/components/page-header/page-header';
import { Pagination } from '../../shared/components/pagination/pagination';
import { SearchBar } from '../../shared/components/search-bar/search-bar';
import { SkeletonLoader } from '../../shared/components/skeleton-loader/skeleton-loader';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { RoleApi } from './role-api.service';

@Component({
  selector: 'app-role-list-page',
  imports: [
    MatButtonModule,
    MatMenuModule,
    RouterLink,
    PageHeader,
    SearchBar,
    Pagination,
    EmptyState,
    SkeletonLoader,
    HasPermissionDirective,
  ],
  templateUrl: './role-list-page.html',
  styleUrls: ['./role-list-page.scss', '../../shared/components/data-table/data-table.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RoleListPage {
  private readonly api = inject(RoleApi);
  private readonly notifications = inject(NotificationService);
  private readonly confirmation = inject(ConfirmationService);

  readonly loading = signal(true);
  readonly roles = signal<RoleRecord[]>([]);
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

  onPage(page: number): void {
    this.page.set(page);
    this.load();
  }

  remove(role: RoleRecord): void {
    this.confirmation
      .confirm({
        title: 'Delete role',
        message: `Delete ${role.name}? Users with this role lose its permissions, and the role cannot be restored.`,
        confirmLabel: 'Delete role',
        cancelLabel: 'Keep role',
        destructive: true,
      })
      .subscribe((confirmed) => {
        if (!confirmed) {
          return;
        }
        this.api.delete(role.id).subscribe({
          next: () => {
            this.notifications.success('Role deleted');
            this.load();
          },
          error: (error: unknown) => this.notifications.error(readApiError(error)),
        });
      });
  }

  private load(): void {
    this.loading.set(true);
    this.api.list(this.search(), this.page(), this.pageSize).subscribe({
      next: (result) => {
        this.roles.set(result.items);
        this.total.set(result.totalElements);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.roles.set([]);
      },
    });
  }
}

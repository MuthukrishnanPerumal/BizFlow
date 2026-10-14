import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';

import { readApiError } from '../../core/utils/http-error.util';
import { formatWhen } from '../master/master-format';
import { EmptyState } from '../../shared/components/empty-state/empty-state';
import { ErrorState } from '../../shared/components/error-state/error-state';
import { PageHeader } from '../../shared/components/page-header/page-header';
import { Pagination } from '../../shared/components/pagination/pagination';
import { SkeletonLoader } from '../../shared/components/skeleton-loader/skeleton-loader';
import { InboxStore } from './inbox.store';
import { NotificationApi } from './notification-api.service';
import { NOTIFICATION_KINDS, AppNotification } from './notification.model';

@Component({
  selector: 'app-notification-page',
  imports: [
    MatButtonModule,
    MatFormFieldModule,
    MatSelectModule,
    PageHeader,
    Pagination,
    EmptyState,
    ErrorState,
    SkeletonLoader,
  ],
  templateUrl: './notification-page.html',
  styleUrl: './notification-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NotificationPage {
  private readonly api = inject(NotificationApi);
  private readonly inbox = inject(InboxStore);

  readonly kinds = NOTIFICATION_KINDS;
  readonly when = formatWhen;
  readonly kind = signal('');
  readonly unreadOnly = signal(false);
  readonly page = signal(0);
  readonly total = signal(0);
  readonly loading = signal(true);
  readonly failed = signal(false);
  readonly errorMessage = signal('');
  readonly items = signal<AppNotification[]>([]);
  readonly pageSize = 20;

  constructor() {
    this.load();
  }

  onKind(value: string): void {
    this.kind.set(value);
    this.page.set(0);
    this.load();
  }

  onUnread(value: boolean): void {
    this.unreadOnly.set(value);
    this.page.set(0);
    this.load();
  }

  onPage(page: number): void {
    this.page.set(page);
    this.load();
  }

  markRead(item: AppNotification): void {
    this.api.markRead(item.id).subscribe({
      next: () => {
        this.inbox.refresh();
        this.load();
      },
      error: (error: unknown) => this.errorMessage.set(readApiError(error)),
    });
  }

  markAll(): void {
    this.api.markAllRead().subscribe({
      next: () => {
        this.inbox.refresh();
        this.load();
      },
      error: (error: unknown) => this.errorMessage.set(readApiError(error)),
    });
  }

  private load(): void {
    this.loading.set(true);
    this.failed.set(false);
    this.api.list(this.kind(), this.unreadOnly(), this.page()).subscribe({
      next: (result) => {
        this.items.set(result.items);
        this.total.set(result.totalElements);
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

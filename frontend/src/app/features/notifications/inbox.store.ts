import { Injectable, inject, signal } from '@angular/core';

import { AuthService } from '../../core/auth/auth.service';
import { NotificationApi } from './notification-api.service';
import { AppNotification } from './notification.model';

@Injectable({ providedIn: 'root' })
export class InboxStore {
  private readonly api = inject(NotificationApi);
  private readonly auth = inject(AuthService);

  readonly items = signal<AppNotification[]>([]);
  readonly unread = signal(0);

  loadSummary(): void {
    if (!this.allowed()) {
      return;
    }
    this.api.summary().subscribe({
      next: (summary) => this.unread.set(summary.unread),
      error: () => this.unread.set(0),
    });
  }

  loadRecent(): void {
    if (!this.allowed()) {
      return;
    }
    this.api.list('', false, 0).subscribe({
      next: (page) => this.items.set(page.items.slice(0, 8)),
      error: () => this.items.set([]),
    });
  }

  refresh(): void {
    this.loadSummary();
    this.loadRecent();
  }

  private allowed(): boolean {
    if (this.auth.hasPermission('NOTIFICATION_VIEW')) {
      return true;
    }
    this.items.set([]);
    this.unread.set(0);
    return false;
  }

  markRead(id: number): void {
    this.api.markRead(id).subscribe({
      next: () => this.refresh(),
    });
  }
}

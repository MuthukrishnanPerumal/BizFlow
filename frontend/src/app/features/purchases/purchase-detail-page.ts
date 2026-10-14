import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';
import { ConfirmationService } from '../../core/services/confirmation.service';
import { NotificationService } from '../../core/services/notification.service';
import { readApiError } from '../../core/utils/http-error.util';
import { formatDate, formatMoney, formatWhen } from '../master/master-format';
import { ErrorState } from '../../shared/components/error-state/error-state';
import { PageHeader } from '../../shared/components/page-header/page-header';
import { SkeletonLoader } from '../../shared/components/skeleton-loader/skeleton-loader';
import { humanStatus, StatusBadge } from '../../shared/components/status-badge/status-badge';
import { PurchaseApi } from './purchase-api.service';
import { PurchaseDetail } from './purchase.model';

@Component({
  selector: 'app-purchase-detail-page',
  imports: [MatButtonModule, RouterLink, PageHeader, ErrorState, SkeletonLoader, StatusBadge],
  templateUrl: './purchase-detail-page.html',
  styleUrls: ['./purchase-screen.scss', '../../shared/components/data-table/data-table.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PurchaseDetailPage {
  private readonly api = inject(PurchaseApi);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly confirmation = inject(ConfirmationService);
  private readonly notifications = inject(NotificationService);
  readonly auth = inject(AuthService);
  readonly money = formatMoney;
  readonly label = humanStatus;
  readonly date = formatDate;
  readonly when = formatWhen;
  readonly loading = signal(true);
  readonly failed = signal(false);
  readonly errorMessage = signal('');
  readonly purchase = signal<PurchaseDetail | null>(null);

  constructor() {
    this.load();
  }

  load(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.loading.set(true);
    this.failed.set(false);
    this.api.get(id).subscribe({
      next: (purchase) => {
        this.purchase.set(purchase);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.failed.set(true);
        this.errorMessage.set(readApiError(error));
        this.loading.set(false);
      },
    });
  }

  confirm(): void {
    const purchase = this.purchase();
    if (!purchase) {
      return;
    }
    this.api.confirm(purchase.id, purchase.version).subscribe({
      next: (updated) => {
        this.purchase.set(updated);
        this.notifications.success('Purchase confirmed');
      },
      error: (error: unknown) => this.notifications.error(readApiError(error)),
    });
  }

  cancel(): void {
    const purchase = this.purchase();
    if (!purchase) {
      return;
    }
    this.confirmation
      .confirm({
        title: 'Cancel purchase',
        message: 'Cancelling reverses stock, the supplier balance, and any payment on this invoice.',
        confirmLabel: 'Cancel purchase',
        cancelLabel: 'Keep purchase',
        destructive: true,
      })
      .subscribe((accepted) => {
        if (!accepted) {
          return;
        }
        this.api.cancel(purchase.id, purchase.version).subscribe({
          next: (updated) => {
            this.purchase.set(updated);
            this.notifications.success('Purchase cancelled');
          },
          error: (error: unknown) => this.notifications.error(readApiError(error)),
        });
      });
  }

  edit(): void {
    const purchase = this.purchase();
    if (purchase) {
      void this.router.navigate(['/purchases', purchase.id, 'edit']);
    }
  }
}

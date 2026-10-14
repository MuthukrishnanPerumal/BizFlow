import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { ConfirmationService } from '../../core/services/confirmation.service';
import { NotificationService } from '../../core/services/notification.service';
import { readApiError } from '../../core/utils/http-error.util';
import { formatDate, formatMoney, formatWhen } from '../master/master-format';
import { ErrorState } from '../../shared/components/error-state/error-state';
import { PageHeader } from '../../shared/components/page-header/page-header';
import { SkeletonLoader } from '../../shared/components/skeleton-loader/skeleton-loader';
import { humanStatus, StatusBadge } from '../../shared/components/status-badge/status-badge';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { SaleApi } from './sale-api.service';
import { SaleDetail } from './sale.model';

@Component({
  selector: 'app-sale-detail-page',
  imports: [
    MatButtonModule,
    RouterLink,
    PageHeader,
    ErrorState,
    SkeletonLoader,
    StatusBadge,
    HasPermissionDirective,
  ],
  templateUrl: './sale-detail-page.html',
  styleUrl: './sale-screen.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SaleDetailPage {
  private readonly api = inject(SaleApi);
  private readonly route = inject(ActivatedRoute);
  private readonly confirm = inject(ConfirmationService);
  private readonly notify = inject(NotificationService);

  readonly money = formatMoney;
  readonly label = humanStatus;
  readonly date = formatDate;
  readonly when = formatWhen;
  readonly loading = signal(true);
  readonly failed = signal(false);
  readonly errorMessage = signal('');
  readonly sale = signal<SaleDetail | null>(null);

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.failed.set(false);
    this.api.get(Number(this.route.snapshot.paramMap.get('id'))).subscribe({
      next: (sale) => {
        this.sale.set(sale);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.loading.set(false);
        this.failed.set(true);
        this.errorMessage.set(readApiError(error));
      },
    });
  }

  cancelSale(): void {
    const sale = this.sale();
    if (!sale) {
      return;
    }
    this.confirm
      .confirm({
        title: 'Cancel sale',
        message: `Cancel ${sale.invoiceNumber}? Stock and the customer balance will be reversed.`,
        confirmLabel: 'Cancel sale',
        cancelLabel: 'Keep sale',
        destructive: true,
      })
      .subscribe((accepted) => {
        if (!accepted) {
          return;
        }
        this.api.cancel(sale.id, sale.version).subscribe({
          next: (updated) => {
            this.sale.set(updated);
            this.notify.success('Sale cancelled');
          },
          error: (error: unknown) => this.notify.error(readApiError(error)),
        });
      });
  }

  complete(): void {
    const sale = this.sale();
    if (!sale) {
      return;
    }
    this.api.confirm(sale.id, sale.version).subscribe({
      next: (updated) => {
        this.sale.set(updated);
        this.notify.success('Sale completed');
      },
      error: (error: unknown) => this.notify.error(readApiError(error)),
    });
  }
}

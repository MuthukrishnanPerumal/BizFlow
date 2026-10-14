import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { ConfirmationService } from '../../core/services/confirmation.service';
import { NotificationService } from '../../core/services/notification.service';
import { readApiError } from '../../core/utils/http-error.util';
import { formatDate, formatMoney } from '../master/master-format';
import { ErrorState } from '../../shared/components/error-state/error-state';
import { PageHeader } from '../../shared/components/page-header/page-header';
import { SkeletonLoader } from '../../shared/components/skeleton-loader/skeleton-loader';
import { StatusBadge } from '../../shared/components/status-badge/status-badge';
import { InventoryApi } from '../inventory/inventory-api.service';
import { formatQty } from '../inventory/inventory-format';
import { InventoryNav } from '../inventory/inventory-nav';
import { StockTransfer } from '../inventory/inventory.model';

@Component({
  selector: 'app-transfer-detail-page',
  imports: [MatButtonModule, RouterLink, PageHeader, ErrorState, SkeletonLoader, StatusBadge, InventoryNav],
  templateUrl: './transfer-detail-page.html',
  styleUrls: ['../master/master-table.scss', '../../shared/components/data-table/data-table.scss', '../inventory/inventory-screen.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TransferDetailPage {
  private readonly api = inject(InventoryApi);
  private readonly route = inject(ActivatedRoute);
  private readonly confirm = inject(ConfirmationService);
  private readonly notify = inject(NotificationService);

  readonly money = formatMoney;
  readonly qty = formatQty;
  readonly date = formatDate;
  readonly loading = signal(true);
  readonly failed = signal(false);
  readonly errorMessage = signal('');
  readonly transfer = signal<StockTransfer | null>(null);
  private readonly transferId = Number(this.route.snapshot.paramMap.get('id'));

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.failed.set(false);
    this.api.transfer(this.transferId).subscribe({
      next: (transfer) => {
        this.transfer.set(transfer);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.loading.set(false);
        this.failed.set(true);
        this.errorMessage.set(readApiError(error));
      },
    });
  }

  confirmTransfer(): void {
    this.run('confirm');
  }

  completeTransfer(): void {
    this.run('complete');
  }

  cancelTransfer(): void {
    const transfer = this.transfer();
    if (!transfer) {
      return;
    }
    this.confirm
      .confirm({
        title: 'Cancel transfer',
        message: `Cancel ${transfer.transferNumber}? A confirmed transfer releases its reservation.`,
        confirmLabel: 'Cancel transfer',
        cancelLabel: 'Keep transfer',
        destructive: true,
      })
      .subscribe((accepted) => {
        if (accepted) {
          this.run('cancel');
        }
      });
  }

  private run(action: 'confirm' | 'complete' | 'cancel'): void {
    const transfer = this.transfer();
    if (!transfer) {
      return;
    }
    const request =
      action === 'confirm'
        ? this.api.confirmTransfer(transfer.id, transfer.version)
        : action === 'complete'
          ? this.api.completeTransfer(transfer.id, transfer.version)
          : this.api.cancelTransfer(transfer.id, transfer.version);
    request.subscribe({
      next: (updated) => {
        this.transfer.set(updated);
        this.notify.success(`${updated.transferNumber} is ${updated.status.replaceAll('_', ' ').toLowerCase()}`);
      },
      error: (error: unknown) => this.notify.error(readApiError(error)),
    });
  }
}

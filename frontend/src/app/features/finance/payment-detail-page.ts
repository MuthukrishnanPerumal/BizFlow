import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { ConfirmationService } from '../../core/services/confirmation.service';
import { readApiError } from '../../core/utils/http-error.util';
import { formatDate, formatMoney, formatWhen } from '../master/master-format';
import { ErrorState } from '../../shared/components/error-state/error-state';
import { PageHeader } from '../../shared/components/page-header/page-header';
import { SkeletonLoader } from '../../shared/components/skeleton-loader/skeleton-loader';
import { StatusBadge } from '../../shared/components/status-badge/status-badge';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { FinanceApi } from './finance-api.service';
import { FinanceNav } from './finance-nav';
import { compareMoney } from './finance-money';
import { PartyKind, PaymentDetail } from './finance.model';

@Component({
  selector: 'app-payment-detail-page',
  imports: [
    MatButtonModule,
    RouterLink,
    PageHeader,
    ErrorState,
    SkeletonLoader,
    StatusBadge,
    HasPermissionDirective,
    FinanceNav,
  ],
  templateUrl: './payment-detail-page.html',
  styleUrls: ['../master/master-table.scss', '../../shared/components/data-table/data-table.scss', './finance-screen.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PaymentDetailPage {
  private readonly api = inject(FinanceApi);
  private readonly route = inject(ActivatedRoute);
  private readonly confirmation = inject(ConfirmationService);

  readonly money = formatMoney;
  readonly date = formatDate;
  readonly when = formatWhen;
  readonly party = signal<PartyKind>('customer');
  readonly loading = signal(true);
  readonly failed = signal(false);
  readonly errorMessage = signal('');
  readonly payment = signal<PaymentDetail | null>(null);
  readonly paymentId = signal(0);

  constructor() {
    const destroyRef = inject(DestroyRef);
    this.route.data.pipe(takeUntilDestroyed(destroyRef)).subscribe((data) => this.party.set(data['party']));
    this.route.paramMap.pipe(takeUntilDestroyed(destroyRef)).subscribe((params) => {
      this.party.set((this.route.snapshot.data['party'] as PartyKind) ?? this.party());
      const id = Number(params.get('id'));
      this.paymentId.set(id);
      if (id) {
        this.load(id);
      }
    });
  }

  balanceClass(value: string | number): string {
    const compared = compareMoney(value, 0);
    if (compared > 0) {
      return 'due';
    }
    if (compared < 0) {
      return 'credit';
    }
    return 'clear';
  }

  cancel(): void {
    const payment = this.payment();
    if (!payment) {
      return;
    }
    this.confirmation
      .confirm({
        title: 'Cancel payment',
        message: 'This reverses the invoice allocations and restores the party balance.',
        confirmLabel: 'Cancel payment',
        cancelLabel: 'Keep payment',
        destructive: true,
      })
      .subscribe((confirmed) => {
        if (!confirmed) {
          return;
        }
        this.api.cancelPayment(this.party(), payment.id, payment.version).subscribe({
          next: (updated) => this.payment.set(updated),
          error: (error: unknown) => this.errorMessage.set(readApiError(error)),
        });
      });
  }

  load(id: number): void {
    this.loading.set(true);
    this.failed.set(false);
    this.api.payment(this.party(), id).subscribe({
      next: (payment) => {
        this.payment.set(payment);
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

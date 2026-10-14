import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { readApiError } from '../../core/utils/http-error.util';
import { formatDate, formatMoney } from '../master/master-format';
import { PurchaseApi } from '../purchases/purchase-api.service';
import { ReturnDetail } from '../purchases/purchase.model';
import { ErrorState } from '../../shared/components/error-state/error-state';
import { PageHeader } from '../../shared/components/page-header/page-header';
import { SkeletonLoader } from '../../shared/components/skeleton-loader/skeleton-loader';
import { StatusBadge } from '../../shared/components/status-badge/status-badge';

@Component({
  selector: 'app-purchase-return-detail-page',
  imports: [MatButtonModule, RouterLink, PageHeader, ErrorState, SkeletonLoader, StatusBadge],
  templateUrl: './purchase-return-detail-page.html',
  styleUrls: ['../purchases/purchase-screen.scss', '../../shared/components/data-table/data-table.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PurchaseReturnDetailPage {
  private readonly api = inject(PurchaseApi);
  private readonly route = inject(ActivatedRoute);
  readonly money = formatMoney;
  readonly date = formatDate;
  readonly loading = signal(true);
  readonly failed = signal(false);
  readonly errorMessage = signal('');
  readonly detail = signal<ReturnDetail | null>(null);

  constructor() {
    this.load();
  }

  load(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.loading.set(true);
    this.failed.set(false);
    this.api.returnDetail(id).subscribe({
      next: (detail) => {
        this.detail.set(detail);
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

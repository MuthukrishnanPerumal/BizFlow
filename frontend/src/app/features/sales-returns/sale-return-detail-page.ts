import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { readApiError } from '../../core/utils/http-error.util';
import { formatDate, formatMoney } from '../master/master-format';
import { ErrorState } from '../../shared/components/error-state/error-state';
import { PageHeader } from '../../shared/components/page-header/page-header';
import { SkeletonLoader } from '../../shared/components/skeleton-loader/skeleton-loader';
import { SaleApi } from '../sales/sale-api.service';
import { ReturnDetail } from '../sales/sale.model';

@Component({
  selector: 'app-sale-return-detail-page',
  imports: [MatButtonModule, RouterLink, PageHeader, ErrorState, SkeletonLoader],
  templateUrl: './sale-return-detail-page.html',
  styleUrl: '../sales/sale-screen.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SaleReturnDetailPage {
  private readonly api = inject(SaleApi);
  private readonly route = inject(ActivatedRoute);
  readonly money = formatMoney;
  readonly date = formatDate;
  readonly loading = signal(true);
  readonly failed = signal(false);
  readonly errorMessage = signal('');
  readonly record = signal<ReturnDetail | null>(null);

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.failed.set(false);
    this.api.returnDetail(Number(this.route.snapshot.paramMap.get('id'))).subscribe({
      next: (record) => {
        this.record.set(record);
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

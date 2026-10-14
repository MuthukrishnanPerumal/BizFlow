import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { formatDate, formatMoney } from '../master/master-format';
import { LetterheadBlock } from '../printing/letterhead';
import { ErrorState } from '../../shared/components/error-state/error-state';
import { readApiError } from '../../core/utils/http-error.util';
import { PurchaseApi } from './purchase-api.service';
import { PurchaseDetail } from './purchase.model';

@Component({
  selector: 'app-purchase-print-page',
  imports: [MatButtonModule, RouterLink, ErrorState, LetterheadBlock],
  templateUrl: './purchase-print-page.html',
  styleUrl: './purchase-screen.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PurchasePrintPage {
  private readonly api = inject(PurchaseApi);
  private readonly route = inject(ActivatedRoute);
  readonly money = formatMoney;
  readonly date = formatDate;
  readonly failed = signal(false);
  readonly errorMessage = signal('');
  readonly purchase = signal<PurchaseDetail | null>(null);

  constructor() {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.api.get(id).subscribe({
      next: (purchase) => this.purchase.set(purchase),
      error: (error: unknown) => {
        this.failed.set(true);
        this.errorMessage.set(readApiError(error));
      },
    });
  }

  print(): void {
    window.print();
  }
}

import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { readApiError } from '../../core/utils/http-error.util';
import { formatDate, formatMoney } from '../master/master-format';
import { LetterheadBlock } from '../printing/letterhead';
import { ErrorState } from '../../shared/components/error-state/error-state';
import { FinanceApi } from './finance-api.service';
import { PartyKind, PaymentDetail } from './finance.model';

@Component({
  selector: 'app-payment-print-page',
  imports: [MatButtonModule, RouterLink, ErrorState, LetterheadBlock],
  templateUrl: './payment-print-page.html',
  styleUrl: '../printing/print-document.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PaymentPrintPage {
  private readonly api = inject(FinanceApi);
  private readonly route = inject(ActivatedRoute);
  readonly money = formatMoney;
  readonly date = formatDate;
  readonly party = signal<PartyKind>('customer');
  readonly failed = signal(false);
  readonly errorMessage = signal('');
  readonly payment = signal<PaymentDetail | null>(null);

  constructor() {
    const party = (this.route.snapshot.data['party'] as PartyKind) ?? 'customer';
    this.party.set(party);
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.api.payment(party, id).subscribe({
      next: (payment) => this.payment.set(payment),
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

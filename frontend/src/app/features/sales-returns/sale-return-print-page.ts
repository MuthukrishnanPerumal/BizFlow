import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { readApiError } from '../../core/utils/http-error.util';
import { formatDate, formatMoney } from '../master/master-format';
import { LetterheadBlock } from '../printing/letterhead';
import { SaleApi } from '../sales/sale-api.service';
import { ReturnDetail } from '../sales/sale.model';
import { ErrorState } from '../../shared/components/error-state/error-state';

@Component({
  selector: 'app-sale-return-print-page',
  imports: [MatButtonModule, RouterLink, ErrorState, LetterheadBlock],
  templateUrl: './sale-return-print-page.html',
  styleUrl: '../printing/print-document.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SaleReturnPrintPage {
  private readonly api = inject(SaleApi);
  private readonly route = inject(ActivatedRoute);
  readonly money = formatMoney;
  readonly date = formatDate;
  readonly failed = signal(false);
  readonly errorMessage = signal('');
  readonly record = signal<ReturnDetail | null>(null);

  constructor() {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.api.returnDetail(id).subscribe({
      next: (record) => this.record.set(record),
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

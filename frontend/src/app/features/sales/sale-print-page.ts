import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { SystemSnapshot } from '../../core/services/system-snapshot.service';
import { LetterheadBlock } from '../printing/letterhead';
import { readApiError } from '../../core/utils/http-error.util';
import { formatDate, formatMoney } from '../master/master-format';
import { ErrorState } from '../../shared/components/error-state/error-state';
import { SaleApi } from './sale-api.service';
import { SaleDetail } from './sale.model';

@Component({
  selector: 'app-sale-print-page',
  imports: [MatButtonModule, RouterLink, ErrorState, LetterheadBlock],
  templateUrl: './sale-print-page.html',
  styleUrl: './sale-screen.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SalePrintPage {
  private readonly api = inject(SaleApi);
  private readonly route = inject(ActivatedRoute);
  readonly snapshot = inject(SystemSnapshot);
  readonly money = formatMoney;
  readonly date = formatDate;
  readonly failed = signal(false);
  readonly errorMessage = signal('');
  readonly sale = signal<SaleDetail | null>(null);

  constructor() {
    if (!this.snapshot.preferences()) {
      this.snapshot.load();
    }
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.api.get(id).subscribe({
      next: (sale) => this.sale.set(sale),
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

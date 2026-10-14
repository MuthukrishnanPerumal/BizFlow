import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { readApiError } from '../../core/utils/http-error.util';
import { formatMoney, formatQty } from '../reporting/reporting-format';
import { LetterheadBlock } from '../printing/letterhead';
import { ReportingApi } from '../reporting/reporting-api.service';
import { ReportPreset, ReportRow } from '../reporting/reporting.model';
import { ErrorState } from '../../shared/components/error-state/error-state';

@Component({
  selector: 'app-stock-report-print-page',
  imports: [MatButtonModule, RouterLink, ErrorState, LetterheadBlock],
  templateUrl: './stock-report-print-page.html',
  styleUrl: '../printing/print-document.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StockReportPrintPage {
  private readonly api = inject(ReportingApi);
  private readonly route = inject(ActivatedRoute);
  readonly money = formatMoney;
  readonly qty = formatQty;
  readonly failed = signal(false);
  readonly errorMessage = signal('');
  readonly rows = signal<ReportRow[]>([]);
  readonly period = signal('');
  readonly total = signal(0);

  constructor() {
    const params = this.route.snapshot.queryParamMap;
    this.api
      .inventoryPrint({
        preset: (params.get('preset') as ReportPreset) || 'THIS_MONTH',
        from: params.get('from') ?? '',
        to: params.get('to') ?? '',
        search: params.get('search') ?? '',
        sort: params.get('sort') ?? 'product,asc',
      })
      .subscribe({
        next: (report) => {
          this.rows.set(report.items);
          this.total.set(report.totalElements);
          this.period.set(`${report.period.from} to ${report.period.to}`);
        },
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

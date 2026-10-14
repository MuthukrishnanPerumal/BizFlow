import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';
import { LedgerEntry, PartyRecord } from '../../core/models/master.model';
import { readApiError } from '../../core/utils/http-error.util';
import { FinanceApi } from '../finance/finance-api.service';
import { OpenInvoice, PaymentSummary } from '../finance/finance.model';
import { PurchaseApi } from '../purchases/purchase-api.service';
import { SaleApi } from '../sales/sale-api.service';
import { EmptyState } from '../../shared/components/empty-state/empty-state';
import { ErrorState } from '../../shared/components/error-state/error-state';
import { FormSection } from '../../shared/components/form-section/form-section';
import { PageHeader } from '../../shared/components/page-header/page-header';
import { Pagination } from '../../shared/components/pagination/pagination';
import { SkeletonLoader } from '../../shared/components/skeleton-loader/skeleton-loader';
import { StatusBadge } from '../../shared/components/status-badge/status-badge';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { formatDate, formatMoney } from './master-format';
import { PartyApi } from './party-api.service';
import { PartyConfig, partyConfig } from './party.config';

type PartyTab = 'overview' | 'documents' | 'payments' | 'outstanding' | 'activity';

interface DocumentRow {
  id: number;
  number: string;
  date: string;
  total: string | number;
  balance: string | number;
  status: string;
}

@Component({
  selector: 'app-party-detail-page',
  imports: [
    MatButtonModule,
    RouterLink,
    PageHeader,
    FormSection,
    StatusBadge,
    SkeletonLoader,
    EmptyState,
    ErrorState,
    Pagination,
    HasPermissionDirective,
  ],
  templateUrl: './party-detail-page.html',
  styleUrls: ['./master-form.scss', '../../shared/components/data-table/data-table.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PartyDetailPage {
  private readonly api = inject(PartyApi);
  private readonly purchases = inject(PurchaseApi);
  private readonly sales = inject(SaleApi);
  private readonly finance = inject(FinanceApi);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  readonly auth = inject(AuthService);

  readonly config: PartyConfig = partyConfig(this.route.snapshot.data['resource']);
  readonly money = formatMoney;
  readonly date = formatDate;
  readonly loading = signal(true);
  readonly failed = signal(false);
  readonly errorMessage = signal('');
  readonly party = signal<PartyRecord | null>(null);
  readonly tab = signal<PartyTab>('overview');
  readonly panelLoading = signal(false);
  readonly panelFailed = signal(false);
  readonly panelError = signal('');
  readonly documents = signal<DocumentRow[]>([]);
  readonly documentTotal = signal(0);
  readonly documentPage = signal(0);
  readonly payments = signal<PaymentSummary[]>([]);
  readonly paymentTotal = signal(0);
  readonly paymentPage = signal(0);
  readonly invoices = signal<OpenInvoice[]>([]);
  readonly entries = signal<LedgerEntry[]>([]);
  readonly page = signal(0);
  readonly total = signal(0);
  readonly pageSize = 20;
  private readonly recordId = Number(this.route.snapshot.paramMap.get('id'));

  constructor() {
    if (!Number.isInteger(this.recordId)) {
      void this.router.navigate(['/', this.config.resource]);
      return;
    }
    this.load();
  }

  get supplier(): boolean {
    return this.config.resource === 'suppliers';
  }

  get canDocuments(): boolean {
    return this.auth.hasPermission(this.supplier ? 'PURCHASE_VIEW' : 'SALES_VIEW');
  }

  get canPayments(): boolean {
    return this.auth.hasPermission('PAYMENT_VIEW');
  }

  codeOf(row: PartyRecord): string {
    return row[this.config.codeField] ?? '';
  }

  nameOf(row: PartyRecord): string {
    return row[this.config.nameField] ?? '';
  }

  openTab(tab: PartyTab): void {
    if ((tab === 'documents' && !this.canDocuments) || ((tab === 'payments' || tab === 'outstanding') && !this.canPayments)) {
      return;
    }
    this.tab.set(tab);
    this.panelFailed.set(false);
    if (tab === 'documents' && this.documents().length === 0) {
      this.loadDocuments();
    } else if (tab === 'payments' && this.payments().length === 0) {
      this.loadPayments();
    } else if (tab === 'outstanding' && this.invoices().length === 0) {
      this.loadOutstanding();
    } else if (tab === 'activity' && this.entries().length === 0) {
      this.loadLedger();
    }
  }

  onDocumentPage(page: number): void {
    this.documentPage.set(page);
    this.loadDocuments();
  }

  onPaymentPage(page: number): void {
    this.paymentPage.set(page);
    this.loadPayments();
  }

  onPage(page: number): void {
    this.page.set(page);
    this.loadLedger();
  }

  load(): void {
    this.loading.set(true);
    this.failed.set(false);
    this.api.get(this.config.resource, this.recordId).subscribe({
      next: (party) => {
        this.party.set(party);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.loading.set(false);
        this.failed.set(true);
        this.errorMessage.set(readApiError(error));
      },
    });
  }

  loadDocuments(): void {
    this.panelLoading.set(true);
    this.panelFailed.set(false);
    if (this.supplier) {
      this.purchases
        .list({
          search: '',
          status: '',
          supplierId: this.recordId,
          invoiceNumber: '',
          dateFrom: '',
          dateTo: '',
          page: this.documentPage(),
          sort: 'purchaseDate,desc',
        })
        .subscribe({
          next: (result) => {
            this.documents.set(
              result.items.map((row) => ({
                id: row.id,
                number: row.invoiceNumber || row.purchaseNumber,
                date: row.purchaseDate,
                total: row.grandTotal,
                balance: row.balanceAmount,
                status: row.status,
              })),
            );
            this.documentTotal.set(result.totalElements);
            this.panelLoading.set(false);
          },
          error: (error: unknown) => this.failPanel(error),
        });
      return;
    }
    this.sales
      .list({
        search: '',
        status: '',
        paymentType: '',
        customerId: this.recordId,
        invoiceNumber: '',
        dateFrom: '',
        dateTo: '',
        page: this.documentPage(),
        sort: 'invoiceDate,desc',
      })
      .subscribe({
        next: (result) => {
          this.documents.set(
            result.items.map((row) => ({
              id: row.id,
              number: row.invoiceNumber,
              date: row.invoiceDate,
              total: row.grandTotal,
              balance: row.balanceAmount,
              status: row.status,
            })),
          );
          this.documentTotal.set(result.totalElements);
          this.panelLoading.set(false);
        },
        error: (error: unknown) => this.failPanel(error),
      });
  }

  loadPayments(): void {
    this.panelLoading.set(true);
    this.panelFailed.set(false);
    const key = this.supplier ? 'supplierId' : 'customerId';
    this.finance
      .payments(this.supplier ? 'supplier' : 'customer', {
        search: '',
        status: '',
        paymentMethod: '',
        [key]: String(this.recordId),
        dateFrom: '',
        dateTo: '',
        minAmount: '',
        maxAmount: '',
        scope: 'ALL',
        page: String(this.paymentPage()),
        size: String(this.pageSize),
        sort: 'paymentDate,desc',
      })
      .subscribe({
        next: (report) => {
          this.payments.set(report.items);
          this.paymentTotal.set(report.totalElements);
          this.panelLoading.set(false);
        },
        error: (error: unknown) => this.failPanel(error),
      });
  }

  loadOutstanding(): void {
    this.panelLoading.set(true);
    this.panelFailed.set(false);
    this.finance.openInvoices(this.supplier ? 'supplier' : 'customer', this.recordId).subscribe({
      next: (invoices) => {
        this.invoices.set(invoices);
        this.panelLoading.set(false);
      },
      error: (error: unknown) => this.failPanel(error),
    });
  }

  loadLedger(): void {
    this.panelLoading.set(true);
    this.panelFailed.set(false);
    this.api.ledger(this.config.resource, this.recordId, this.page()).subscribe({
      next: (result) => {
        this.entries.set(result.items);
        this.total.set(result.totalElements);
        this.panelLoading.set(false);
      },
      error: (error: unknown) => this.failPanel(error),
    });
  }

  private failPanel(error: unknown): void {
    this.panelLoading.set(false);
    this.panelFailed.set(true);
    this.panelError.set(readApiError(error));
  }

  retryPanel(): void {
    const tab = this.tab();
    if (tab === 'documents') {
      this.loadDocuments();
    } else if (tab === 'payments') {
      this.loadPayments();
    } else if (tab === 'outstanding') {
      this.loadOutstanding();
    } else if (tab === 'activity') {
      this.loadLedger();
    }
  }
}

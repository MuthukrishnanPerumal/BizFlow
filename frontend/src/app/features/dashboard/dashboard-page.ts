import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';
import { EMPTY, Subject, catchError, switchMap } from 'rxjs';

import { AuthSession } from '../../core/auth/auth-session.service';
import { readApiError } from '../../core/utils/http-error.util';
import { EmptyState } from '../../shared/components/empty-state/empty-state';
import { ErrorState } from '../../shared/components/error-state/error-state';
import { IsoDateField } from '../../shared/components/iso-date-field/iso-date-field';
import { PageHeader } from '../../shared/components/page-header/page-header';
import { StatusBadge } from '../../shared/components/status-badge/status-badge';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { dateFormatVersion, formatDate } from '../master/master-format';
import { InboxStore } from '../notifications/inbox.store';
import { MetricChart } from '../reporting/metric-chart';
import { ReportingApi } from '../reporting/reporting-api.service';
import { formatMoney, formatPeriod, formatQty } from '../reporting/reporting-format';
import { DashboardResponse, PRESET_OPTIONS, PeriodQuery, ReportPreset } from '../reporting/reporting.model';
import {
  activityCopy,
  buildAttention,
  buildKpis,
  chartEmpty,
  movementQuantity,
  paymentRoute,
  periodName,
} from './dashboard-view';

const QUICK_ACTIONS = [
  { label: 'New sale', detail: 'Create a customer invoice', route: '/sales/new', icon: 'point_of_sale', permission: 'SALES_CREATE' },
  { label: 'New purchase', detail: 'Record a supplier bill', route: '/purchases/new', icon: 'shopping_cart', permission: 'PURCHASE_CREATE' },
  { label: 'Add product', detail: 'Add an item to the catalog', route: '/products/new', icon: 'inventory_2', permission: 'PRODUCT_CREATE' },
  { label: 'Receive payment', detail: 'Record a customer receipt', route: '/payments/customer/new', icon: 'payments', permission: 'PAYMENT_CREATE' },
  { label: 'Stock adjustment', detail: 'Correct quantity on hand', route: '/stock-adjustment/new', icon: 'tune', permission: 'INVENTORY_ADJUST' },
  { label: 'View reports', detail: 'Open sales, stock, and profit', route: '/reports', icon: 'assessment', permission: 'REPORT_VIEW' },
] as const;

type ActivityTab = 'sales' | 'purchases' | 'payments' | 'stock';

@Component({
  selector: 'app-dashboard-page',
  imports: [
    MatButtonModule,
    MatIconModule,
    RouterLink,
    PageHeader,
    IsoDateField,
    MetricChart,
    EmptyState,
    ErrorState,
    StatusBadge,
    HasPermissionDirective,
  ],
  templateUrl: './dashboard-page.html',
  styleUrl: './dashboard-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardPage {
  private readonly api = inject(ReportingApi);
  private readonly session = inject(AuthSession);
  private readonly inbox = inject(InboxStore);
  private readonly reload = new Subject<void>();

  readonly money = formatMoney;
  readonly qty = formatQty;
  readonly date = formatDate;
  readonly presets = PRESET_OPTIONS.map((option) =>
    option.id === 'CUSTOM' ? { ...option, label: 'Custom range' } : option,
  );
  readonly quickActions = QUICK_ACTIONS;
  readonly activityTabs: readonly { id: ActivityTab; label: string }[] = [
    { id: 'sales', label: 'Sales' },
    { id: 'purchases', label: 'Purchases' },
    { id: 'payments', label: 'Payments' },
    { id: 'stock', label: 'Stock' },
  ];
  readonly skeletonKpis = [0, 1, 2, 3, 4, 5, 6];
  readonly skeletonActions = [0, 1, 2, 3, 4, 5];
  readonly skeletonCharts = [0, 1, 2];

  readonly allowed = computed(() => this.session.hasPermission('REPORT_VIEW'));
  readonly preset = signal<ReportPreset>('THIS_MONTH');
  readonly from = signal('');
  readonly to = signal('');
  readonly loading = signal(false);
  readonly failed = signal(false);
  readonly errorMessage = signal('');
  readonly data = signal<DashboardResponse | null>(null);
  readonly drafts = signal<{ sales: number | null; purchases: number | null }>({ sales: null, purchases: null });
  readonly activity = signal<ActivityTab>('sales');

  readonly periodLabel = computed(() => formatPeriod(this.data()?.period.from, this.data()?.period.to));
  readonly subtitle = computed(() => this.periodLabel() || periodName(this.preset(), this.from(), this.to()));
  readonly showQuickActions = computed(() =>
    QUICK_ACTIONS.some((action) => this.session.hasPermission(action.permission)),
  );

  readonly access = computed(() => ({
    reports: this.session.hasPermission('REPORT_VIEW'),
    sales: this.session.hasPermission('SALES_VIEW'),
    purchases: this.session.hasPermission('PURCHASE_VIEW'),
    inventory: this.session.hasPermission('INVENTORY_VIEW'),
    receivables: this.session.hasPermission('RECEIVABLE_VIEW'),
    payables: this.session.hasPermission('PAYABLE_VIEW'),
    notifications: this.session.hasPermission('NOTIFICATION_VIEW'),
  }));

  readonly kpis = computed(() => {
    dateFormatVersion();
    const board = this.data();
    return board ? buildKpis(board, formatMoney, this.access()) : [];
  });

  readonly attention = computed(() => {
    dateFormatVersion();
    const board = this.data();
    if (!board) {
      return [];
    }
    return buildAttention(board, this.drafts(), this.inbox.unread(), this.access(), formatMoney);
  });

  readonly salesSeries = computed(() => [
    { name: 'Sales', color: 'var(--bf-accent)', points: this.data()?.charts.salesTrend ?? [] },
  ]);
  readonly purchaseSeries = computed(() => [
    { name: 'Purchases', color: 'var(--bf-accent)', points: this.data()?.charts.purchaseTrend ?? [] },
  ]);
  readonly profitSeries = computed(() => [
    {
      name: 'Net profit',
      color: 'var(--bf-accent)',
      points: (this.data()?.charts.profitTrend ?? []).map((point) => ({ label: point.label, amount: point.netProfit })),
    },
  ]);
  readonly grainCaption = computed(() => {
    const grain = this.data()?.period.grain;
    if (grain === 'MONTH') {
      return 'By month';
    }
    if (grain === 'YEAR') {
      return 'By year';
    }
    return 'By day';
  });

  readonly activityLink = computed(() => {
    const access = this.access();
    switch (this.activity()) {
      case 'sales':
        return access.sales ? { label: 'All sales', route: '/sales' } : null;
      case 'purchases':
        return access.purchases ? { label: 'All purchases', route: '/purchases' } : null;
      case 'payments':
        return this.session.hasPermission('PAYMENT_VIEW') ? { label: 'All payments', route: '/payments/customer' } : null;
      default:
        return access.inventory ? { label: 'Stock ledger', route: '/inventory/ledger' } : null;
    }
  });

  constructor() {
    this.reload
      .pipe(
        switchMap(() => this.fetch()),
        takeUntilDestroyed(inject(DestroyRef)),
      )
      .subscribe((board) => {
        this.data.set(board);
        this.drafts.set({
          sales: board.drafts?.sales ?? null,
          purchases: board.drafts?.purchases ?? null,
        });
        this.loading.set(false);
      });
    if (this.allowed()) {
      this.load();
    }
  }

  onPreset(preset: ReportPreset): void {
    this.preset.set(preset);
    if (preset !== 'CUSTOM' || (this.from() && this.to())) {
      this.load();
    }
  }

  onFrom(value: string): void {
    this.from.set(value);
    if (this.to()) {
      this.load();
    }
  }

  onTo(value: string): void {
    this.to.set(value);
    if (this.from()) {
      this.load();
    }
  }

  load(): void {
    this.reload.next();
  }

  selectActivity(tab: ActivityTab): void {
    this.activity.set(tab);
  }

  salesEmpty(): { title: string; message: string } {
    return activityCopy('sales', this.data()?.kpis.totalSales ?? 0);
  }

  purchasesEmpty(): { title: string; message: string } {
    return activityCopy('purchases', this.data()?.kpis.totalPurchases ?? 0);
  }

  paymentsEmpty(): { title: string; message: string } {
    return activityCopy('payments', this.data()?.widgets.recentPayments.length ?? 0);
  }

  stockEmpty(): { title: string; message: string } {
    return activityCopy('stock', this.data()?.widgets.recentMovements.length ?? 0);
  }

  salesChartEmpty(): string {
    return chartEmpty('sales', (this.data()?.kpis.totalSales ?? 0) === 0);
  }

  purchasesChartEmpty(): string {
    return chartEmpty('purchases', (this.data()?.kpis.totalPurchases ?? 0) === 0);
  }

  profitChartEmpty(): string {
    const board = this.data();
    return chartEmpty('profit', !!board && board.kpis.grossProfit === 0 && board.kpis.netProfit === 0);
  }

  movement(quantityIn: number, quantityOut: number): string {
    return movementQuantity(quantityIn, quantityOut, formatQty);
  }

  paymentLink(direction: string, id: number): string {
    return paymentRoute(direction, id);
  }

  canOpenPayment(): boolean {
    return this.session.hasPermission('PAYMENT_VIEW');
  }

  private fetch() {
    if (!this.allowed() || (this.preset() === 'CUSTOM' && (!this.from() || !this.to()))) {
      return EMPTY;
    }
    this.loading.set(true);
    this.failed.set(false);
    const query: PeriodQuery = { preset: this.preset(), from: this.from(), to: this.to() };
    return this.api.dashboard(query).pipe(
      catchError((error: unknown) => {
        this.failed.set(true);
        this.errorMessage.set(readApiError(error));
        this.loading.set(false);
        return EMPTY;
      }),
    );
  }
}

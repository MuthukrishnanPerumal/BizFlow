import { formatPeriod } from '../reporting/reporting-format';
import { DashboardResponse, PRESET_OPTIONS } from '../reporting/reporting.model';

export interface KpiItem {
  id: string;
  label: string;
  value: string;
  period: string;
  trend: string | null;
  spark: string | null;
  negative: boolean;
  actionLabel: string | null;
  actionRoute: string | null;
}

export interface AttentionItem {
  id: string;
  title: string;
  detail: string;
  route: string;
  query?: Record<string, string>;
}

export interface DashboardAccess {
  reports: boolean;
  sales: boolean;
  purchases: boolean;
  inventory: boolean;
  receivables: boolean;
  payables: boolean;
  notifications: boolean;
}

export function periodName(preset: string, from?: string, to?: string): string {
  if (preset === 'CUSTOM') {
    return formatPeriod(from, to) || 'Custom range';
  }
  return PRESET_OPTIONS.find((option) => option.id === preset)?.label ?? 'Selected period';
}

export function trendLabel(amounts: readonly number[], grain: string): string | null {
  if (amounts.length < 2) {
    return null;
  }
  const current = amounts[amounts.length - 1] ?? 0;
  const previous = amounts[amounts.length - 2] ?? 0;
  if (current === 0 && previous === 0) {
    return null;
  }
  const prior = grain === 'MONTH' ? 'prior month' : grain === 'YEAR' ? 'prior year' : 'prior day';
  if (current === previous) {
    return `No change vs ${prior}`;
  }
  const direction = current > previous ? '↑' : '↓';
  if (previous === 0) {
    return `${direction} vs ${prior}`;
  }
  const pct = (Math.abs(current - previous) / Math.abs(previous)) * 100;
  const shown = pct >= 999 ? '999%+' : pct >= 10 ? `${Math.round(pct)}%` : `${Math.round(pct * 10) / 10}%`;
  return `${direction} ${shown} vs ${prior}`;
}

export function sparkline(amounts: readonly number[]): string | null {
  if (amounts.length < 2 || amounts.every((amount) => amount === 0)) {
    return null;
  }
  const width = 88;
  const height = 28;
  const max = Math.max(...amounts);
  const min = Math.min(...amounts);
  const span = max - min || 1;
  return amounts
    .map((amount, index) => {
      const x = (index / (amounts.length - 1)) * width;
      const y = height - ((amount - min) / span) * (height - 4) - 2;
      return `${x.toFixed(1)},${y.toFixed(1)}`;
    })
    .join(' ');
}

export function buildKpis(
  board: DashboardResponse,
  money: (value: number) => string,
  access: DashboardAccess,
): KpiItem[] {
  const period = periodName(board.period.preset, board.period.from, board.period.to);
  const grain = board.period.grain;
  const sales = board.charts.salesTrend.map((point) => point.amount);
  const purchases = board.charts.purchaseTrend.map((point) => point.amount);
  const gross = board.charts.profitTrend.map((point) => point.grossProfit);
  const net = board.charts.profitTrend.map((point) => point.netProfit);
  const kpis = board.kpis;

  return [
    kpi('sales', 'Sales', money(kpis.totalSales), period, trendLabel(sales, grain), sparkline(sales), false, access.reports ? 'Sales report' : null, access.reports ? '/reports/sales' : null),
    kpi('purchases', 'Purchases', money(kpis.totalPurchases), period, trendLabel(purchases, grain), sparkline(purchases), false, access.reports ? 'Purchase report' : null, access.reports ? '/reports/purchases' : null),
    kpi('gross', 'Gross profit', money(kpis.grossProfit), period, trendLabel(gross, grain), sparkline(gross), kpis.grossProfit < 0, null, null),
    kpi('net', 'Net profit', money(kpis.netProfit), period, trendLabel(net, grain), sparkline(net), kpis.netProfit < 0, access.reports ? 'Profit report' : null, access.reports ? '/reports/profit' : null),
    kpi('receivables', 'Receivables', money(kpis.receivables), `${period} · Open balances`, null, null, false, access.receivables && kpis.receivables > 0 ? 'Collect' : null, access.receivables && kpis.receivables > 0 ? '/receivables' : null),
    kpi('payables', 'Payables', money(kpis.payables), `${period} · Open balances`, null, null, false, access.payables && kpis.payables > 0 ? 'Review' : null, access.payables && kpis.payables > 0 ? '/payables' : null),
    kpi('stock', 'Stock value', money(kpis.totalStockValue), `${period} · At period end`, null, null, false, access.inventory ? 'Valuation' : null, access.inventory ? '/inventory/valuation' : null),
  ];
}

export function buildAttention(
  board: DashboardResponse,
  drafts: { sales: number | null; purchases: number | null },
  unread: number,
  access: DashboardAccess,
  money: (value: number) => string,
): AttentionItem[] {
  const items: AttentionItem[] = [];
  const low = board.kpis.lowStockItems;
  const out = board.kpis.outOfStockItems;

  if (access.inventory && low > 0) {
    items.push({
      id: 'low-stock',
      title: 'Low stock',
      detail: `${count(low, 'product is', 'products are')} at or below reorder level`,
      route: '/inventory/low-stock',
    });
  }
  if (access.inventory && out > 0) {
    items.push({
      id: 'out-of-stock',
      title: 'Out of stock',
      detail: `${count(out, 'product has', 'products have')} no quantity on hand`,
      route: '/inventory/out-of-stock',
    });
  }
  if (access.receivables && board.kpis.receivables > 0) {
    items.push({
      id: 'receivables',
      title: 'Outstanding payments',
      detail: `Customers owe ${money(board.kpis.receivables)}`,
      route: '/receivables',
    });
  }
  if (access.payables && board.kpis.payables > 0) {
    items.push({
      id: 'payables',
      title: 'Outstanding payments',
      detail: `Suppliers are owed ${money(board.kpis.payables)}`,
      route: '/payables',
    });
  }
  if (access.sales && (drafts.sales ?? 0) > 0) {
    const sales = drafts.sales ?? 0;
    items.push({
      id: 'pending-sales',
      title: 'Pending transactions',
      detail: `${count(sales, 'sales draft is', 'sales drafts are')} waiting to be confirmed`,
      route: '/sales',
      query: { status: 'DRAFT' },
    });
  }
  if (access.purchases && (drafts.purchases ?? 0) > 0) {
    const purchases = drafts.purchases ?? 0;
    items.push({
      id: 'pending-purchases',
      title: 'Pending transactions',
      detail: `${count(purchases, 'purchase draft is', 'purchase drafts are')} waiting to be confirmed`,
      route: '/purchases',
      query: { status: 'DRAFT' },
    });
  }
  if (access.notifications && unread > 0) {
    items.push({
      id: 'alerts',
      title: 'Important alerts',
      detail: `${count(unread, 'unread notification', 'unread notifications')}`,
      route: '/notifications',
    });
  }
  return items;
}

export function activityCopy(
  kind: 'sales' | 'purchases' | 'payments' | 'stock',
  periodTotal: number,
): { title: string; message: string } {
  if (kind === 'sales') {
    return periodTotal === 0
      ? { title: 'No sales yet', message: 'Create your first sale to start tracking business activity.' }
      : { title: 'No recent sales', message: 'Sales posted in this period will appear here.' };
  }
  if (kind === 'purchases') {
    return periodTotal === 0
      ? { title: 'No purchases yet', message: 'Record a supplier bill to start tracking purchases.' }
      : { title: 'No recent purchases', message: 'Purchases posted in this period will appear here.' };
  }
  if (kind === 'payments') {
    return periodTotal === 0
      ? { title: 'No payments yet', message: 'Receive a customer payment to see collections here.' }
      : { title: 'No recent payments', message: 'Posted receipts and payments in this period will appear here.' };
  }
  return periodTotal === 0
    ? { title: 'No stock movements yet', message: 'Sales, purchases, and adjustments will appear here.' }
    : { title: 'No recent stock movements', message: 'Stock changes in this period will appear here.' };
}

export function chartEmpty(kind: 'sales' | 'purchases' | 'profit', idle: boolean): string {
  if (!idle) {
    return 'No movement in this period.';
  }
  if (kind === 'sales') {
    return 'No sales yet. Create your first sale to start tracking business activity.';
  }
  if (kind === 'purchases') {
    return 'No purchases yet. Record a supplier bill to see the purchasing trend.';
  }
  return 'No profit movement yet. Sales in this period will build this trend.';
}

export function movementQuantity(
  quantityIn: number,
  quantityOut: number,
  format: (value: number) => string,
): string {
  if (quantityIn > 0 && quantityOut > 0) {
    return `+${format(quantityIn)} / −${format(quantityOut)}`;
  }
  if (quantityIn > 0) {
    return `+${format(quantityIn)}`;
  }
  if (quantityOut > 0) {
    return `−${format(quantityOut)}`;
  }
  return format(0);
}

export function paymentRoute(direction: string, id: number): string {
  return direction === 'OUT' ? `/payments/supplier/${id}` : `/payments/customer/${id}`;
}

function kpi(
  id: string,
  label: string,
  value: string,
  period: string,
  trend: string | null,
  spark: string | null,
  negative: boolean,
  actionLabel: string | null,
  actionRoute: string | null,
): KpiItem {
  return { id, label, value, period, trend, spark, negative, actionLabel, actionRoute };
}

function count(value: number, singular: string, plural: string): string {
  return `${value} ${value === 1 ? singular : plural}`;
}

export type ReportPreset =
  | 'TODAY'
  | 'YESTERDAY'
  | 'THIS_WEEK'
  | 'THIS_MONTH'
  | 'PREVIOUS_MONTH'
  | 'THIS_YEAR'
  | 'CUSTOM';

export type DocumentScope =
  | 'POSTED'
  | 'ALL'
  | 'DRAFT'
  | 'CONFIRMED'
  | 'COMPLETED'
  | 'PARTIALLY_RETURNED'
  | 'RETURNED'
  | 'CANCELLED';

export interface PeriodQuery {
  preset: ReportPreset;
  from: string;
  to: string;
}

export interface PeriodView {
  preset: ReportPreset;
  from: string;
  to: string;
  grain: 'DAY' | 'MONTH' | 'YEAR';
}

export interface StatementView {
  grossSales: number;
  salesReturns: number;
  revenue: number;
  costOfGoodsSold: number;
  grossProfit: number;
  expenses: number;
  netProfit: number;
}

export interface KpiView {
  totalSales: number;
  totalPurchases: number;
  grossProfit: number;
  netProfit: number;
  totalStockValue: number;
  lowStockItems: number;
  outOfStockItems: number;
  receivables: number;
  payables: number;
  expenses: number;
}

export interface AmountPoint {
  key: string;
  label: string;
  amount: number;
}

export interface ProductPoint {
  key: string;
  label: string;
  amount: number;
  quantity: number;
}

export interface ProfitPoint {
  key: string;
  label: string;
  revenue: number;
  costOfGoodsSold: number;
  grossProfit: number;
  netProfit: number;
}

export interface RecentDocument {
  id: number;
  number: string;
  date: string;
  party: string;
  amount: number;
  status: string;
}

export interface StockAlert {
  id: number;
  product: string;
  category: string;
  onHand: number;
  reorderLevel: number;
}

export interface PartyRank {
  id: number;
  name: string;
  documents: number;
  amount: number;
}

export interface RecentPayment {
  id: number;
  number: string;
  date: string;
  party: string;
  direction: string;
  method: string;
  amount: number;
  status: string;
}

export interface StockMovement {
  id: number;
  date: string;
  product: string;
  type: string;
  reference: string | null;
  quantityIn: number;
  quantityOut: number;
  amount: number;
}

export interface DashboardResponse {
  period: PeriodView;
  kpis: KpiView;
  statement: StatementView;
  charts: {
    salesTrend: AmountPoint[];
    purchaseTrend: AmountPoint[];
    profitTrend: ProfitPoint[];
    salesByCategory: AmountPoint[];
    topProducts: ProductPoint[];
    purchasesBySupplier: AmountPoint[];
    stockValueByCategory: AmountPoint[];
    paymentBreakdown: AmountPoint[];
  };
  widgets: {
    recentSales: RecentDocument[];
    recentPurchases: RecentDocument[];
    lowStock: StockAlert[];
    outOfStock: StockAlert[];
    topCustomers: PartyRank[];
    topSuppliers: PartyRank[];
    recentPayments: RecentPayment[];
    recentMovements: StockMovement[];
  };
}

export interface ReportPageResult<T> {
  period: PeriodView;
  items: T[];
  totals?: Record<string, number>;
  statement?: StatementView;
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ReportRow {
  id?: number;
  invoice?: string;
  date?: string;
  customer?: string;
  supplier?: string;
  product?: string;
  period?: string;
  subtotal?: number;
  discount?: number;
  tax?: number;
  total?: number;
  paid?: number;
  balance?: number;
  status?: string;
  openingStock?: number;
  purchase?: number;
  purchaseReturn?: number;
  sales?: number;
  salesReturn?: number;
  adjustment?: number;
  closingStock?: number;
  stockValue?: number;
  revenue?: number;
  costOfGoodsSold?: number;
  grossProfit?: number;
  expenses?: number;
  netProfit?: number;
}

export const PRESET_OPTIONS: readonly { id: ReportPreset; label: string }[] = [
  { id: 'TODAY', label: 'Today' },
  { id: 'YESTERDAY', label: 'Yesterday' },
  { id: 'THIS_WEEK', label: 'This week' },
  { id: 'THIS_MONTH', label: 'This month' },
  { id: 'PREVIOUS_MONTH', label: 'Previous month' },
  { id: 'THIS_YEAR', label: 'This year' },
  { id: 'CUSTOM', label: 'Custom' },
];

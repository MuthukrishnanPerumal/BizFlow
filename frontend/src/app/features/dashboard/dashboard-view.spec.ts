import { buildAttention, periodName, sparkline, trendLabel } from './dashboard-view';
import { DashboardResponse } from '../reporting/reporting.model';

describe('dashboard view', () => {
  it('names the selected comparison period', () => {
    expect(periodName('THIS_MONTH')).toBe('This month');
    expect(periodName('PREVIOUS_MONTH')).toBe('Previous month');
    expect(periodName('CUSTOM', '2026-10-01', '2026-10-05')).toContain('2026');
  });

  it('describes a trend only when a prior bucket exists', () => {
    expect(trendLabel([10], 'DAY')).toBeNull();
    expect(trendLabel([0, 0], 'DAY')).toBeNull();
    expect(trendLabel([100, 112], 'DAY')).toBe('↑ 12% vs prior day');
    expect(trendLabel([200, 150], 'MONTH')).toBe('↓ 25% vs prior month');
    expect(trendLabel([40, 40], 'YEAR')).toBe('No change vs prior year');
    expect(trendLabel([0, 20], 'DAY')).toBe('↑ vs prior day');
  });

  it('draws a sparkline from the series the API already returned', () => {
    expect(sparkline([0, 0, 0])).toBeNull();
    expect(sparkline([4])).toBeNull();
    expect(sparkline([1, 3, 2])?.split(' ')).toHaveLength(3);
  });

  it('builds attention items from dashboard figures and draft counts', () => {
    const items = buildAttention(
      board(),
      { sales: 2, purchases: 0 },
      1,
      {
        reports: true,
        sales: true,
        purchases: true,
        inventory: true,
        receivables: true,
        payables: true,
        notifications: true,
      },
      (value) => `₹${value}`,
    );

    expect(items.map((item) => item.id)).toEqual([
      'low-stock',
      'out-of-stock',
      'receivables',
      'payables',
      'pending-sales',
      'alerts',
    ]);
    expect(items.find((item) => item.id === 'pending-sales')?.query).toEqual({ status: 'DRAFT' });
    expect(items.find((item) => item.id === 'receivables')?.detail).toBe('Customers owe ₹500');
  });
});

function board(): DashboardResponse {
  return {
    period: { preset: 'THIS_MONTH', from: '2026-10-01', to: '2026-10-05', grain: 'DAY' },
    kpis: {
      totalSales: 0,
      totalPurchases: 0,
      grossProfit: 0,
      netProfit: 0,
      totalStockValue: 0,
      lowStockItems: 3,
      outOfStockItems: 1,
      receivables: 500,
      payables: 80,
      expenses: 0,
    },
    statement: {
      grossSales: 0,
      salesReturns: 0,
      revenue: 0,
      costOfGoodsSold: 0,
      grossProfit: 0,
      expenses: 0,
      netProfit: 0,
    },
    charts: {
      salesTrend: [],
      purchaseTrend: [],
      profitTrend: [],
      salesByCategory: [],
      topProducts: [],
      purchasesBySupplier: [],
      stockValueByCategory: [],
      paymentBreakdown: [],
    },
    widgets: {
      recentSales: [],
      recentPurchases: [],
      lowStock: [],
      outOfStock: [],
      topCustomers: [],
      topSuppliers: [],
      recentPayments: [],
      recentMovements: [],
    },
  };
}

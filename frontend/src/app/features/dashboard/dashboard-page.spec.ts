import { signal } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { AuthSession } from '../../core/auth/auth-session.service';
import { InboxStore } from '../notifications/inbox.store';
import { ReportingApi } from '../reporting/reporting-api.service';
import { DashboardResponse } from '../reporting/reporting.model';
import { DashboardPage } from './dashboard-page';

describe('DashboardPage', () => {
  let allowReports = true;
  const dashboard = vi.fn(() => of(sample()));
  const report = vi.fn(() => of({ totalElements: 2 }));

  beforeEach(async () => {
    allowReports = true;
    dashboard.mockClear();
    report.mockClear();
    await TestBed.configureTestingModule({
      imports: [DashboardPage],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        {
          provide: AuthSession,
          useValue: { hasPermission: (permission: string) => (permission === 'REPORT_VIEW' ? allowReports : true) },
        },
        { provide: InboxStore, useValue: { unread: signal(1), items: signal([]) } },
        { provide: ReportingApi, useValue: { dashboard, report } },
      ],
    }).compileComponents();
  });

  it('renders the command center from the dashboard response', () => {
    const fixture = TestBed.createComponent(DashboardPage);
    fixture.detectChanges();
    const text = fixture.nativeElement.textContent as string;

    expect(text).toContain('Business summary');
    expect(text).toContain('Quick actions');
    expect(text).toContain('Attention required');
    expect(text).toContain('Business trends');
    expect(text).toContain('Recent activity');
    expect(text).toContain('Sales');
    expect(text).toContain('Gross profit');
    expect(text).toContain('Net profit');
    expect(text).toContain('Receivables');
    expect(text).toContain('Payables');
    expect(text).toContain('Stock value');
    expect(text).toContain('New sale');
    expect(text).toContain('Receive payment');
    expect(text).toContain('Low stock');
    expect(text).toContain('Out of stock');
    expect(text).toContain('Outstanding payments');
    expect(text).toContain('Pending transactions');
    expect(text).toContain('Important alerts');
    expect(text).toContain('INV-1');
    expect(text).toContain('↑ 12% vs prior day');
    expect(fixture.nativeElement.querySelector('a[href="/inventory/low-stock"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('a[href="/sales/new"]')).toBeTruthy();
    expect(dashboard).toHaveBeenCalled();
  });

  it('switches recent activity and period without inventing figures', () => {
    const fixture = TestBed.createComponent(DashboardPage);
    fixture.detectChanges();
    const purchases = [...fixture.nativeElement.querySelectorAll('button')].find(
      (button: HTMLButtonElement) => button.textContent?.trim() === 'Purchases',
    ) as HTMLButtonElement;
    purchases.click();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('BILL-9');

    const today = [...fixture.nativeElement.querySelectorAll('button')].find(
      (button: HTMLButtonElement) => button.textContent?.trim() === 'Today',
    ) as HTMLButtonElement;
    today.click();
    fixture.detectChanges();
    expect(dashboard).toHaveBeenCalledWith(expect.objectContaining({ preset: 'TODAY' }));
    expect(report).toHaveBeenCalledWith('sales', expect.objectContaining({ preset: 'TODAY' }), expect.objectContaining({ scope: 'DRAFT', size: 1 }));
  });

  it('explains missing report access instead of showing empty numbers', () => {
    allowReports = false;
    const fixture = TestBed.createComponent(DashboardPage);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Report access required');
    expect(dashboard).not.toHaveBeenCalled();
  });
});

function sample(): DashboardResponse {
  return {
    period: { preset: 'THIS_MONTH', from: '2026-10-01', to: '2026-10-05', grain: 'DAY' },
    kpis: {
      totalSales: 1120,
      totalPurchases: 800,
      grossProfit: 300,
      netProfit: 220,
      totalStockValue: 5000,
      lowStockItems: 2,
      outOfStockItems: 1,
      receivables: 450,
      payables: 90,
      expenses: 80,
    },
    statement: {
      grossSales: 1120,
      salesReturns: 0,
      revenue: 1120,
      costOfGoodsSold: 820,
      grossProfit: 300,
      expenses: 80,
      netProfit: 220,
    },
    charts: {
      salesTrend: [
        { key: '2026-10-04', label: '4 Oct', amount: 500 },
        { key: '2026-10-05', label: '5 Oct', amount: 560 },
      ],
      purchaseTrend: [
        { key: '2026-10-04', label: '4 Oct', amount: 100 },
        { key: '2026-10-05', label: '5 Oct', amount: 120 },
      ],
      profitTrend: [
        { key: '2026-10-04', label: '4 Oct', revenue: 500, costOfGoodsSold: 300, grossProfit: 200, netProfit: 150 },
        { key: '2026-10-05', label: '5 Oct', revenue: 560, costOfGoodsSold: 320, grossProfit: 240, netProfit: 180 },
      ],
      salesByCategory: [],
      topProducts: [],
      purchasesBySupplier: [],
      stockValueByCategory: [],
      paymentBreakdown: [],
    },
    widgets: {
      recentSales: [{ id: 4, number: 'INV-1', date: '2026-10-05', party: 'Northwind', amount: 560, status: 'CONFIRMED' }],
      recentPurchases: [{ id: 9, number: 'BILL-9', date: '2026-10-04', party: 'Acme', amount: 120, status: 'COMPLETED' }],
      lowStock: [],
      outOfStock: [],
      topCustomers: [],
      topSuppliers: [],
      recentPayments: [],
      recentMovements: [],
    },
  };
}

import { Routes } from '@angular/router';

import { permissionGuard } from '../../core/guards/auth.guard';

const report = (path: string, title: string, kind: string) => ({
  path,
  canActivate: [permissionGuard],
  loadComponent: () => import('./report-page').then((module) => module.ReportPage),
  data: {
    title,
    breadcrumb: title,
    permission: 'REPORT_VIEW',
    report: kind,
  },
});

export const REPORTS_ROUTES: Routes = [
  {
    path: '',
    canActivate: [permissionGuard],
    loadComponent: () => import('./reports-home-page').then((module) => module.ReportsHomePage),
    data: {
      title: 'Reports',
      breadcrumb: 'Reports',
      permission: 'REPORT_VIEW',
    },
  },
  report('sales', 'Sales report', 'sales'),
  report('purchases', 'Purchase report', 'purchases'),
  {
    path: 'inventory/print',
    canActivate: [permissionGuard],
    loadComponent: () => import('./stock-report-print-page').then((module) => module.StockReportPrintPage),
    data: { title: 'Print stock report', breadcrumb: 'Print', permission: 'REPORT_VIEW' },
  },
  report('inventory', 'Inventory report', 'inventory'),
  report('profit', 'Profit report', 'profit'),
];

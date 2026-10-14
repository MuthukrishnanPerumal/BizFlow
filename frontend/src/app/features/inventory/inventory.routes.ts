import { Routes } from '@angular/router';

import { permissionGuard } from '../../core/guards/auth.guard';

const view = { permission: 'INVENTORY_VIEW' };

export const INVENTORY_ROUTES: Routes = [
  {
    path: '',
    canActivate: [permissionGuard],
    loadComponent: () => import('./inventory-home-page').then((module) => module.InventoryHomePage),
    data: { title: 'Inventory', breadcrumb: 'Inventory', ...view },
  },
  {
    path: 'overview',
    canActivate: [permissionGuard],
    loadComponent: () => import('./stock-overview-page').then((module) => module.StockOverviewPage),
    data: { title: 'Stock overview', breadcrumb: 'Overview', ...view },
  },
  {
    path: 'ledger',
    canActivate: [permissionGuard],
    loadComponent: () => import('./stock-ledger-page').then((module) => module.StockLedgerPage),
    data: { title: 'Stock ledger', breadcrumb: 'Ledger', ...view },
  },
  {
    path: 'low-stock',
    canActivate: [permissionGuard],
    loadComponent: () => import('./low-stock-page').then((module) => module.LowStockPage),
    data: { title: 'Low stock', breadcrumb: 'Low stock', ...view },
  },
  {
    path: 'out-of-stock',
    canActivate: [permissionGuard],
    loadComponent: () => import('./out-of-stock-page').then((module) => module.OutOfStockPage),
    data: { title: 'Out of stock', breadcrumb: 'Out of stock', ...view },
  },
  {
    path: 'valuation',
    canActivate: [permissionGuard],
    loadComponent: () => import('./valuation-page').then((module) => module.ValuationPage),
    data: { title: 'Inventory valuation', breadcrumb: 'Valuation', ...view },
  },
  {
    path: 'products/:productId',
    canActivate: [permissionGuard],
    loadComponent: () => import('./product-stock-page').then((module) => module.ProductStockPage),
    data: { title: 'Product stock', breadcrumb: 'Product', ...view },
  },
];

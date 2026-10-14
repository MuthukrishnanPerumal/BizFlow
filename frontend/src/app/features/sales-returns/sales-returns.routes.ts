import { Routes } from '@angular/router';

import { permissionGuard } from '../../core/guards/auth.guard';
import { unsavedChangesGuard } from '../../core/guards/unsaved-changes.guard';

export const SALES_RETURNS_ROUTES: Routes = [
  {
    path: '',
    canActivate: [permissionGuard],
    loadComponent: () => import('./sale-return-list-page').then((module) => module.SaleReturnListPage),
    data: { title: 'Sales returns', breadcrumb: 'Sales returns', permission: 'SALES_VIEW' },
  },
  {
    path: 'new',
    canActivate: [permissionGuard],
    canDeactivate: [unsavedChangesGuard],
    loadComponent: () => import('./sale-return-form-page').then((module) => module.SaleReturnFormPage),
    data: { title: 'Sales return', breadcrumb: 'New return', permission: 'SALES_CREATE' },
  },
  {
    path: ':id/print',
    canActivate: [permissionGuard],
    loadComponent: () => import('./sale-return-print-page').then((module) => module.SaleReturnPrintPage),
    data: { title: 'Print sales return', breadcrumb: 'Print', permission: 'SALES_VIEW' },
  },
  {
    path: ':id',
    canActivate: [permissionGuard],
    loadComponent: () => import('./sale-return-detail-page').then((module) => module.SaleReturnDetailPage),
    data: { title: 'Sales return', breadcrumb: 'Sales return', permission: 'SALES_VIEW' },
  },
];

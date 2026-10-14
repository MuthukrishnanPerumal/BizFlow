import { Routes } from '@angular/router';

import { permissionGuard } from '../../core/guards/auth.guard';
import { unsavedChangesGuard } from '../../core/guards/unsaved-changes.guard';

export const PURCHASE_RETURNS_ROUTES: Routes = [
  {
    path: '',
    canActivate: [permissionGuard],
    loadComponent: () => import('./purchase-return-list-page').then((module) => module.PurchaseReturnListPage),
    data: { title: 'Purchase returns', breadcrumb: 'Purchase returns', permission: 'PURCHASE_VIEW' },
  },
  {
    path: 'new',
    canActivate: [permissionGuard],
    canDeactivate: [unsavedChangesGuard],
    loadComponent: () => import('./purchase-return-form-page').then((module) => module.PurchaseReturnFormPage),
    data: { title: 'Return goods', breadcrumb: 'Return goods', permission: 'PURCHASE_CREATE' },
  },
  {
    path: ':id/print',
    canActivate: [permissionGuard],
    loadComponent: () => import('./purchase-return-print-page').then((module) => module.PurchaseReturnPrintPage),
    data: { title: 'Print purchase return', breadcrumb: 'Print', permission: 'PURCHASE_VIEW' },
  },
  {
    path: ':id',
    canActivate: [permissionGuard],
    loadComponent: () => import('./purchase-return-detail-page').then((module) => module.PurchaseReturnDetailPage),
    data: { title: 'Purchase return', breadcrumb: 'Purchase return', permission: 'PURCHASE_VIEW' },
  },
];

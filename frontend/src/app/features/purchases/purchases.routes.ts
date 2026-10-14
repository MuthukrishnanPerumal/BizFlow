import { Routes } from '@angular/router';

import { permissionGuard } from '../../core/guards/auth.guard';
import { unsavedChangesGuard } from '../../core/guards/unsaved-changes.guard';

export const PURCHASES_ROUTES: Routes = [
  {
    path: '',
    canActivate: [permissionGuard],
    loadComponent: () => import('./purchase-list-page').then((module) => module.PurchaseListPage),
    data: { title: 'Purchases', breadcrumb: 'Purchases', permission: 'PURCHASE_VIEW' },
  },
  {
    path: 'new',
    canActivate: [permissionGuard],
    canDeactivate: [unsavedChangesGuard],
    loadComponent: () => import('./purchase-form-page').then((module) => module.PurchaseFormPage),
    data: { title: 'New purchase', breadcrumb: 'New purchase', permission: 'PURCHASE_CREATE' },
  },
  {
    path: ':id/edit',
    canActivate: [permissionGuard],
    canDeactivate: [unsavedChangesGuard],
    loadComponent: () => import('./purchase-form-page').then((module) => module.PurchaseFormPage),
    data: { title: 'Edit purchase', breadcrumb: 'Edit purchase', permission: 'PURCHASE_UPDATE' },
  },
  {
    path: ':id/print',
    canActivate: [permissionGuard],
    loadComponent: () => import('./purchase-print-page').then((module) => module.PurchasePrintPage),
    data: { title: 'Print purchase', breadcrumb: 'Print', permission: 'PURCHASE_VIEW' },
  },
  {
    path: ':id',
    canActivate: [permissionGuard],
    loadComponent: () => import('./purchase-detail-page').then((module) => module.PurchaseDetailPage),
    data: { title: 'Purchase', breadcrumb: 'Purchase', permission: 'PURCHASE_VIEW' },
  },
];

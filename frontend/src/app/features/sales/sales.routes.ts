import { Routes } from '@angular/router';

import { permissionGuard } from '../../core/guards/auth.guard';
import { unsavedChangesGuard } from '../../core/guards/unsaved-changes.guard';

export const SALES_ROUTES: Routes = [
  {
    path: '',
    canActivate: [permissionGuard],
    loadComponent: () => import('./sale-list-page').then((module) => module.SaleListPage),
    data: { title: 'Sales', breadcrumb: 'Sales', permission: 'SALES_VIEW' },
  },
  {
    path: 'new',
    canActivate: [permissionGuard],
    canDeactivate: [unsavedChangesGuard],
    loadComponent: () => import('./sale-form-page').then((module) => module.SaleFormPage),
    data: { title: 'New sale', breadcrumb: 'New sale', permission: 'SALES_CREATE' },
  },
  {
    path: ':id/edit',
    canActivate: [permissionGuard],
    canDeactivate: [unsavedChangesGuard],
    loadComponent: () => import('./sale-form-page').then((module) => module.SaleFormPage),
    data: { title: 'Edit sale', breadcrumb: 'Edit sale', permission: 'SALES_UPDATE' },
  },
  {
    path: ':id/print',
    canActivate: [permissionGuard],
    loadComponent: () => import('./sale-print-page').then((module) => module.SalePrintPage),
    data: { title: 'Print invoice', breadcrumb: 'Print', permission: 'SALES_VIEW' },
  },
  {
    path: ':id',
    canActivate: [permissionGuard],
    loadComponent: () => import('./sale-detail-page').then((module) => module.SaleDetailPage),
    data: { title: 'Sale', breadcrumb: 'Sale', permission: 'SALES_VIEW' },
  },
];

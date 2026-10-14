import { Routes } from '@angular/router';

import { permissionGuard } from '../../core/guards/auth.guard';
import { unsavedChangesGuard } from '../../core/guards/unsaved-changes.guard';

export const STOCK_TRANSFER_ROUTES: Routes = [
  {
    path: '',
    canActivate: [permissionGuard],
    loadComponent: () => import('./transfer-list-page').then((module) => module.TransferListPage),
    data: { title: 'Stock transfer', breadcrumb: 'Stock transfer', permission: 'INVENTORY_TRANSFER' },
  },
  {
    path: 'new',
    canActivate: [permissionGuard],
    canDeactivate: [unsavedChangesGuard],
    loadComponent: () => import('./transfer-form-page').then((module) => module.TransferFormPage),
    data: { title: 'New transfer', breadcrumb: 'New transfer', permission: 'INVENTORY_TRANSFER' },
  },
  {
    path: ':id',
    canActivate: [permissionGuard],
    loadComponent: () => import('./transfer-detail-page').then((module) => module.TransferDetailPage),
    data: { title: 'Transfer', breadcrumb: 'Transfer', permission: 'INVENTORY_TRANSFER' },
  },
];

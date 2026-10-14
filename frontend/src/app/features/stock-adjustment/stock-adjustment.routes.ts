import { Routes } from '@angular/router';

import { permissionGuard } from '../../core/guards/auth.guard';
import { unsavedChangesGuard } from '../../core/guards/unsaved-changes.guard';

export const STOCK_ADJUSTMENT_ROUTES: Routes = [
  {
    path: '',
    canActivate: [permissionGuard],
    loadComponent: () => import('./adjustment-list-page').then((module) => module.AdjustmentListPage),
    data: { title: 'Stock adjustment', breadcrumb: 'Stock adjustment', permission: 'INVENTORY_ADJUST' },
  },
  {
    path: 'new',
    canActivate: [permissionGuard],
    canDeactivate: [unsavedChangesGuard],
    loadComponent: () => import('./adjustment-form-page').then((module) => module.AdjustmentFormPage),
    data: { title: 'New adjustment', breadcrumb: 'New adjustment', permission: 'INVENTORY_ADJUST' },
  },
];

import { Routes } from '@angular/router';

import { unsavedChangesGuard } from '../../core/guards/unsaved-changes.guard';
import { permissionGuard } from '../../core/guards/auth.guard';

export const SUPPLIERS_ROUTES: Routes = [
  {
    path: '',
    canActivate: [permissionGuard],
    loadComponent: () => import('../master/party-list-page').then((module) => module.PartyListPage),
    data: { title: 'Suppliers', breadcrumb: 'Suppliers', permission: 'SUPPLIER_VIEW', resource: 'suppliers' },
  },
  {
    path: 'new',
    canActivate: [permissionGuard],
    canDeactivate: [unsavedChangesGuard],
    loadComponent: () => import('../master/party-form-page').then((module) => module.PartyFormPage),
    data: { title: 'New supplier', breadcrumb: 'New supplier', permission: 'SUPPLIER_CREATE', resource: 'suppliers' },
  },
  {
    path: ':id',
    canActivate: [permissionGuard],
    loadComponent: () => import('../master/party-detail-page').then((module) => module.PartyDetailPage),
    data: { title: 'Supplier', breadcrumb: 'Supplier', permission: 'SUPPLIER_VIEW', resource: 'suppliers' },
  },
  {
    path: ':id/edit',
    canActivate: [permissionGuard],
    canDeactivate: [unsavedChangesGuard],
    loadComponent: () => import('../master/party-form-page').then((module) => module.PartyFormPage),
    data: { title: 'Edit supplier', breadcrumb: 'Edit supplier', permission: 'SUPPLIER_VIEW', resource: 'suppliers' },
  },
];

import { Routes } from '@angular/router';

import { unsavedChangesGuard } from '../../core/guards/unsaved-changes.guard';
import { permissionGuard } from '../../core/guards/auth.guard';

export const CUSTOMERS_ROUTES: Routes = [
  {
    path: '',
    canActivate: [permissionGuard],
    loadComponent: () => import('../master/party-list-page').then((module) => module.PartyListPage),
    data: { title: 'Customers', breadcrumb: 'Customers', permission: 'CUSTOMER_VIEW', resource: 'customers' },
  },
  {
    path: 'new',
    canActivate: [permissionGuard],
    canDeactivate: [unsavedChangesGuard],
    loadComponent: () => import('../master/party-form-page').then((module) => module.PartyFormPage),
    data: { title: 'New customer', breadcrumb: 'New customer', permission: 'CUSTOMER_CREATE', resource: 'customers' },
  },
  {
    path: ':id',
    canActivate: [permissionGuard],
    loadComponent: () => import('../master/party-detail-page').then((module) => module.PartyDetailPage),
    data: { title: 'Customer', breadcrumb: 'Customer', permission: 'CUSTOMER_VIEW', resource: 'customers' },
  },
  {
    path: ':id/edit',
    canActivate: [permissionGuard],
    canDeactivate: [unsavedChangesGuard],
    loadComponent: () => import('../master/party-form-page').then((module) => module.PartyFormPage),
    data: { title: 'Edit customer', breadcrumb: 'Edit customer', permission: 'CUSTOMER_VIEW', resource: 'customers' },
  },
];

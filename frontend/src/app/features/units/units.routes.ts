import { Routes } from '@angular/router';

import { unsavedChangesGuard } from '../../core/guards/unsaved-changes.guard';
import { permissionGuard } from '../../core/guards/auth.guard';

export const UNITS_ROUTES: Routes = [
  {
    path: '',
    canActivate: [permissionGuard],
    loadComponent: () => import('../master/catalog-list-page').then((module) => module.CatalogListPage),
    data: { title: 'Units', breadcrumb: 'Units', permission: 'PRODUCT_VIEW', resource: 'units' },
  },
  {
    path: 'new',
    canActivate: [permissionGuard],
    canDeactivate: [unsavedChangesGuard],
    loadComponent: () => import('../master/catalog-form-page').then((module) => module.CatalogFormPage),
    data: { title: 'New unit', breadcrumb: 'New unit', permission: 'PRODUCT_CREATE', resource: 'units' },
  },
  {
    path: ':id',
    canActivate: [permissionGuard],
    canDeactivate: [unsavedChangesGuard],
    loadComponent: () => import('../master/catalog-form-page').then((module) => module.CatalogFormPage),
    data: { title: 'Edit unit', breadcrumb: 'Edit unit', permission: 'PRODUCT_VIEW', resource: 'units' },
  },
];

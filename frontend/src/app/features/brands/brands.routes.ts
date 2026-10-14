import { Routes } from '@angular/router';

import { unsavedChangesGuard } from '../../core/guards/unsaved-changes.guard';
import { permissionGuard } from '../../core/guards/auth.guard';

export const BRANDS_ROUTES: Routes = [
  {
    path: '',
    canActivate: [permissionGuard],
    loadComponent: () => import('../master/catalog-list-page').then((module) => module.CatalogListPage),
    data: { title: 'Brands', breadcrumb: 'Brands', permission: 'PRODUCT_VIEW', resource: 'brands' },
  },
  {
    path: 'new',
    canActivate: [permissionGuard],
    canDeactivate: [unsavedChangesGuard],
    loadComponent: () => import('../master/catalog-form-page').then((module) => module.CatalogFormPage),
    data: { title: 'New brand', breadcrumb: 'New brand', permission: 'PRODUCT_CREATE', resource: 'brands' },
  },
  {
    path: ':id',
    canActivate: [permissionGuard],
    canDeactivate: [unsavedChangesGuard],
    loadComponent: () => import('../master/catalog-form-page').then((module) => module.CatalogFormPage),
    data: { title: 'Edit brand', breadcrumb: 'Edit brand', permission: 'PRODUCT_VIEW', resource: 'brands' },
  },
];

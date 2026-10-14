import { Routes } from '@angular/router';

import { unsavedChangesGuard } from '../../core/guards/unsaved-changes.guard';
import { permissionGuard } from '../../core/guards/auth.guard';

export const CATEGORIES_ROUTES: Routes = [
  {
    path: '',
    canActivate: [permissionGuard],
    loadComponent: () => import('../master/catalog-list-page').then((module) => module.CatalogListPage),
    data: { title: 'Categories', breadcrumb: 'Categories', permission: 'PRODUCT_VIEW', resource: 'categories' },
  },
  {
    path: 'new',
    canActivate: [permissionGuard],
    canDeactivate: [unsavedChangesGuard],
    loadComponent: () => import('../master/catalog-form-page').then((module) => module.CatalogFormPage),
    data: { title: 'New category', breadcrumb: 'New category', permission: 'PRODUCT_CREATE', resource: 'categories' },
  },
  {
    path: ':id',
    canActivate: [permissionGuard],
    canDeactivate: [unsavedChangesGuard],
    loadComponent: () => import('../master/catalog-form-page').then((module) => module.CatalogFormPage),
    data: { title: 'Edit category', breadcrumb: 'Edit category', permission: 'PRODUCT_VIEW', resource: 'categories' },
  },
];

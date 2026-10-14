import { Routes } from '@angular/router';

import { unsavedChangesGuard } from '../../core/guards/unsaved-changes.guard';
import { permissionGuard } from '../../core/guards/auth.guard';

export const PRODUCTS_ROUTES: Routes = [
  {
    path: '',
    canActivate: [permissionGuard],
    loadComponent: () => import('./product-list-page').then((module) => module.ProductListPage),
    data: { title: 'Products', breadcrumb: 'Products', permission: 'PRODUCT_VIEW' },
  },
  {
    path: 'new',
    canActivate: [permissionGuard],
    canDeactivate: [unsavedChangesGuard],
    loadComponent: () => import('./product-form-page').then((module) => module.ProductFormPage),
    data: { title: 'New product', breadcrumb: 'New product', permission: 'PRODUCT_CREATE' },
  },
  {
    path: ':id',
    canActivate: [permissionGuard],
    canDeactivate: [unsavedChangesGuard],
    loadComponent: () => import('./product-form-page').then((module) => module.ProductFormPage),
    data: { title: 'Edit product', breadcrumb: 'Edit product', permission: 'PRODUCT_VIEW' },
  },
];

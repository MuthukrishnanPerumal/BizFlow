import { Routes } from '@angular/router';

import { permissionGuard } from '../../core/guards/auth.guard';
import { unsavedChangesGuard } from '../../core/guards/unsaved-changes.guard';

export const PAYMENTS_ROUTES: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'customer' },
  {
    path: 'customer',
    canActivate: [permissionGuard],
    loadComponent: () => import('../finance/payment-list-page').then((module) => module.PaymentListPage),
    data: { title: 'Customer payments', breadcrumb: 'Customer payments', permission: 'PAYMENT_VIEW', party: 'customer' },
  },
  {
    path: 'customer/new',
    canActivate: [permissionGuard],
    canDeactivate: [unsavedChangesGuard],
    loadComponent: () => import('../finance/payment-form-page').then((module) => module.PaymentFormPage),
    data: { title: 'New customer payment', breadcrumb: 'New payment', permission: 'PAYMENT_CREATE', party: 'customer' },
  },
  {
    path: 'customer/:id/edit',
    canActivate: [permissionGuard],
    canDeactivate: [unsavedChangesGuard],
    loadComponent: () => import('../finance/payment-form-page').then((module) => module.PaymentFormPage),
    data: { title: 'Edit customer payment', breadcrumb: 'Edit payment', permission: 'PAYMENT_UPDATE', party: 'customer' },
  },
  {
    path: 'customer/:id/print',
    canActivate: [permissionGuard],
    loadComponent: () => import('../finance/payment-print-page').then((module) => module.PaymentPrintPage),
    data: { title: 'Print receipt', breadcrumb: 'Print', permission: 'PAYMENT_VIEW', party: 'customer' },
  },
  {
    path: 'customer/:id',
    canActivate: [permissionGuard],
    loadComponent: () => import('../finance/payment-detail-page').then((module) => module.PaymentDetailPage),
    data: { title: 'Customer payment', breadcrumb: 'Payment', permission: 'PAYMENT_VIEW', party: 'customer' },
  },
  {
    path: 'supplier',
    canActivate: [permissionGuard],
    loadComponent: () => import('../finance/payment-list-page').then((module) => module.PaymentListPage),
    data: { title: 'Supplier payments', breadcrumb: 'Supplier payments', permission: 'PAYMENT_VIEW', party: 'supplier' },
  },
  {
    path: 'supplier/new',
    canActivate: [permissionGuard],
    canDeactivate: [unsavedChangesGuard],
    loadComponent: () => import('../finance/payment-form-page').then((module) => module.PaymentFormPage),
    data: { title: 'New supplier payment', breadcrumb: 'New payment', permission: 'PAYMENT_CREATE', party: 'supplier' },
  },
  {
    path: 'supplier/:id/edit',
    canActivate: [permissionGuard],
    canDeactivate: [unsavedChangesGuard],
    loadComponent: () => import('../finance/payment-form-page').then((module) => module.PaymentFormPage),
    data: { title: 'Edit supplier payment', breadcrumb: 'Edit payment', permission: 'PAYMENT_UPDATE', party: 'supplier' },
  },
  {
    path: 'supplier/:id/print',
    canActivate: [permissionGuard],
    loadComponent: () => import('../finance/payment-print-page').then((module) => module.PaymentPrintPage),
    data: { title: 'Print voucher', breadcrumb: 'Print', permission: 'PAYMENT_VIEW', party: 'supplier' },
  },
  {
    path: 'supplier/:id',
    canActivate: [permissionGuard],
    loadComponent: () => import('../finance/payment-detail-page').then((module) => module.PaymentDetailPage),
    data: { title: 'Supplier payment', breadcrumb: 'Payment', permission: 'PAYMENT_VIEW', party: 'supplier' },
  },
];

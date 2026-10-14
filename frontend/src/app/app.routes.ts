import { Routes } from '@angular/router';

import { authGuard, guestGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/login-page').then((module) => module.LoginPage),
    data: { title: 'Sign in' },
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./layout/shell/shell').then((module) => module.Shell),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      {
        path: 'account/password',
        loadComponent: () =>
          import('./features/auth/change-password-page').then((module) => module.ChangePasswordPage),
        data: { title: 'Change password', breadcrumb: 'Password' },
      },
      {
        path: 'forbidden',
        loadComponent: () => import('./features/auth/forbidden-page').then((module) => module.ForbiddenPage),
        data: { title: 'Access denied', breadcrumb: 'Access denied' },
      },
      {
        path: 'dashboard',
        loadChildren: () => import('./features/dashboard/dashboard.routes').then((module) => module.DASHBOARD_ROUTES),
      },
      {
        path: 'products',
        loadChildren: () => import('./features/products/products.routes').then((module) => module.PRODUCTS_ROUTES),
      },
      {
        path: 'categories',
        loadChildren: () => import('./features/categories/categories.routes').then((module) => module.CATEGORIES_ROUTES),
      },
      {
        path: 'brands',
        loadChildren: () => import('./features/brands/brands.routes').then((module) => module.BRANDS_ROUTES),
      },
      {
        path: 'units',
        loadChildren: () => import('./features/units/units.routes').then((module) => module.UNITS_ROUTES),
      },
      {
        path: 'suppliers',
        loadChildren: () => import('./features/suppliers/suppliers.routes').then((module) => module.SUPPLIERS_ROUTES),
      },
      {
        path: 'customers',
        loadChildren: () => import('./features/customers/customers.routes').then((module) => module.CUSTOMERS_ROUTES),
      },
      {
        path: 'purchases',
        loadChildren: () => import('./features/purchases/purchases.routes').then((module) => module.PURCHASES_ROUTES),
      },
      {
        path: 'purchase-returns',
        loadChildren: () =>
          import('./features/purchase-returns/purchase-returns.routes').then((module) => module.PURCHASE_RETURNS_ROUTES),
      },
      {
        path: 'sales',
        loadChildren: () => import('./features/sales/sales.routes').then((module) => module.SALES_ROUTES),
      },
      {
        path: 'sales-returns',
        loadChildren: () =>
          import('./features/sales-returns/sales-returns.routes').then((module) => module.SALES_RETURNS_ROUTES),
      },
      {
        path: 'inventory',
        loadChildren: () => import('./features/inventory/inventory.routes').then((module) => module.INVENTORY_ROUTES),
      },
      {
        path: 'stock-adjustment',
        loadChildren: () =>
          import('./features/stock-adjustment/stock-adjustment.routes').then((module) => module.STOCK_ADJUSTMENT_ROUTES),
      },
      {
        path: 'stock-transfer',
        loadChildren: () =>
          import('./features/stock-transfer/stock-transfer.routes').then((module) => module.STOCK_TRANSFER_ROUTES),
      },
      {
        path: 'payments',
        loadChildren: () => import('./features/payments/payments.routes').then((module) => module.PAYMENTS_ROUTES),
      },
      {
        path: 'expenses',
        loadChildren: () => import('./features/expenses/expenses.routes').then((module) => module.EXPENSES_ROUTES),
      },
      {
        path: 'receivables',
        loadChildren: () => import('./features/finance/receivables.routes').then((module) => module.RECEIVABLES_ROUTES),
      },
      {
        path: 'payables',
        loadChildren: () => import('./features/finance/payables.routes').then((module) => module.PAYABLES_ROUTES),
      },
      {
        path: 'reports',
        loadChildren: () => import('./features/reports/reports.routes').then((module) => module.REPORTS_ROUTES),
      },
      {
        path: 'users',
        loadChildren: () => import('./features/users/users.routes').then((module) => module.USERS_ROUTES),
      },
      {
        path: 'roles',
        loadChildren: () => import('./features/roles/roles.routes').then((module) => module.ROLES_ROUTES),
      },
      {
        path: 'permissions',
        loadChildren: () =>
          import('./features/permissions/permissions.routes').then((module) => module.PERMISSIONS_ROUTES),
      },
      {
        path: 'notifications',
        loadChildren: () =>
          import('./features/notifications/notifications.routes').then((module) => module.NOTIFICATIONS_ROUTES),
      },
      {
        path: 'audit-logs',
        loadChildren: () => import('./features/audit-logs/audit-logs.routes').then((module) => module.AUDIT_LOGS_ROUTES),
      },
      {
        path: 'settings',
        loadChildren: () => import('./features/settings/settings.routes').then((module) => module.SETTINGS_ROUTES),
      },
      {
        path: '**',
        loadComponent: () => import('./layout/not-found/not-found').then((module) => module.NotFoundPage),
        data: { title: 'Not found', breadcrumb: 'Not found' },
      },
    ],
  },
  {
    path: 'access-required',
    loadComponent: () => import('./layout/access-required/access-required').then((module) => module.AccessRequired),
    data: { title: 'Authentication required' },
  },
];

import { Routes } from '@angular/router';

import { permissionGuard } from '../../core/guards/auth.guard';
import { unsavedChangesGuard } from '../../core/guards/unsaved-changes.guard';

export const EXPENSES_ROUTES: Routes = [
  {
    path: '',
    canActivate: [permissionGuard],
    loadComponent: () => import('../finance/expense-list-page').then((module) => module.ExpenseListPage),
    data: { title: 'Expenses', breadcrumb: 'Expenses', permission: 'EXPENSE_VIEW' },
  },
  {
    path: 'new',
    canActivate: [permissionGuard],
    canDeactivate: [unsavedChangesGuard],
    loadComponent: () => import('../finance/expense-form-page').then((module) => module.ExpenseFormPage),
    data: { title: 'New expense', breadcrumb: 'New expense', permission: 'EXPENSE_CREATE' },
  },
  {
    path: ':id/edit',
    canActivate: [permissionGuard],
    canDeactivate: [unsavedChangesGuard],
    loadComponent: () => import('../finance/expense-form-page').then((module) => module.ExpenseFormPage),
    data: { title: 'Edit expense', breadcrumb: 'Edit expense', permission: 'EXPENSE_UPDATE' },
  },
  {
    path: ':id',
    canActivate: [permissionGuard],
    loadComponent: () => import('../finance/expense-detail-page').then((module) => module.ExpenseDetailPage),
    data: { title: 'Expense', breadcrumb: 'Expense', permission: 'EXPENSE_VIEW' },
  },
];

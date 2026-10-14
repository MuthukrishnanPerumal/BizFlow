import { Routes } from '@angular/router';

import { permissionGuard } from '../../core/guards/auth.guard';

export const ROLES_ROUTES: Routes = [
  {
    path: '',
    canActivate: [permissionGuard],
    loadComponent: () => import('./role-list-page').then((module) => module.RoleListPage),
    data: { title: 'Roles', breadcrumb: 'Roles', permission: 'ROLE_VIEW' },
  },
  {
    path: 'new',
    canActivate: [permissionGuard],
    loadComponent: () => import('./role-form-page').then((module) => module.RoleFormPage),
    data: { title: 'New role', breadcrumb: 'New role', permission: 'ROLE_CREATE' },
  },
  {
    path: ':id',
    canActivate: [permissionGuard],
    loadComponent: () => import('./role-form-page').then((module) => module.RoleFormPage),
    data: { title: 'Edit role', breadcrumb: 'Edit role', permission: 'ROLE_VIEW' },
  },
];

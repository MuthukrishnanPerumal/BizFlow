import { Routes } from '@angular/router';

import { permissionGuard } from '../../core/guards/auth.guard';

export const USERS_ROUTES: Routes = [
  {
    path: '',
    canActivate: [permissionGuard],
    loadComponent: () => import('./user-list-page').then((module) => module.UserListPage),
    data: { title: 'Users', breadcrumb: 'Users', permission: 'USER_VIEW' },
  },
  {
    path: 'new',
    canActivate: [permissionGuard],
    loadComponent: () => import('./user-form-page').then((module) => module.UserFormPage),
    data: { title: 'New user', breadcrumb: 'New user', permission: 'USER_CREATE' },
  },
  {
    path: ':id',
    canActivate: [permissionGuard],
    loadComponent: () => import('./user-form-page').then((module) => module.UserFormPage),
    data: { title: 'Edit user', breadcrumb: 'Edit user', permission: 'USER_VIEW' },
  },
];

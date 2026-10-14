import { Routes } from '@angular/router';

import { permissionGuard } from '../../core/guards/auth.guard';

export const PERMISSIONS_ROUTES: Routes = [
  {
    path: '',
    canActivate: [permissionGuard],
    loadComponent: () => import('./permission-list-page').then((module) => module.PermissionListPage),
    data: { title: 'Permissions', breadcrumb: 'Permissions', permission: 'ROLE_VIEW' },
  },
];

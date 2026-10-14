import { Routes } from '@angular/router';

import { permissionGuard } from '../../core/guards/auth.guard';

export const NOTIFICATIONS_ROUTES: Routes = [
  {
    path: '',
    canActivate: [permissionGuard],
    loadComponent: () => import('./notification-page').then((module) => module.NotificationPage),
    data: { title: 'Notifications', breadcrumb: 'Notifications', permission: 'NOTIFICATION_VIEW' },
  },
];

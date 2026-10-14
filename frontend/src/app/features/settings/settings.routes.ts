import { Routes } from '@angular/router';

import { permissionGuard } from '../../core/guards/auth.guard';
import { unsavedChangesGuard } from '../../core/guards/unsaved-changes.guard';

export const SETTINGS_ROUTES: Routes = [
  {
    path: '',
    canActivate: [permissionGuard],
    canDeactivate: [unsavedChangesGuard],
    loadComponent: () => import('./settings-page').then((module) => module.SettingsPage),
    data: { title: 'Settings', breadcrumb: 'Settings', permission: 'SETTINGS_VIEW' },
  },
];

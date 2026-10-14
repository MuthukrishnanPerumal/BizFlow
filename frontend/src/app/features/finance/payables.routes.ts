import { Routes } from '@angular/router';

import { permissionGuard } from '../../core/guards/auth.guard';

export const PAYABLES_ROUTES: Routes = [
  {
    path: '',
    canActivate: [permissionGuard],
    loadComponent: () => import('./position-page').then((module) => module.PositionPage),
    data: { title: 'Payables', breadcrumb: 'Payables', permission: 'PAYABLE_VIEW', kind: 'payable' },
  },
];

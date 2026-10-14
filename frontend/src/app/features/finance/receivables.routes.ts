import { Routes } from '@angular/router';

import { permissionGuard } from '../../core/guards/auth.guard';

export const RECEIVABLES_ROUTES: Routes = [
  {
    path: '',
    canActivate: [permissionGuard],
    loadComponent: () => import('./position-page').then((module) => module.PositionPage),
    data: { title: 'Receivables', breadcrumb: 'Receivables', permission: 'RECEIVABLE_VIEW', kind: 'receivable' },
  },
];

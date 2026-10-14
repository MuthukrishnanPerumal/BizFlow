import { Routes } from '@angular/router';

import { permissionGuard } from '../../core/guards/auth.guard';

export const AUDIT_LOGS_ROUTES: Routes = [
  {
    path: '',
    canActivate: [permissionGuard],
    loadComponent: () => import('../audit/audit-log-page').then((module) => module.AuditLogPage),
    data: { title: 'Audit logs', breadcrumb: 'Audit logs', permission: 'AUDIT_VIEW' },
  },
];

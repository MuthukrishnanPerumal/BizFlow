import { PageResult } from '../../core/models/identity.model';

export interface AuditSummary {
  id: number;
  user: string;
  action: string;
  module: string;
  entity: string | null;
  entityId: string | null;
  outcome: string;
  detail: string | null;
  ipAddress: string | null;
  timestamp: string;
}

export interface AuditDetail extends AuditSummary {
  eventType: string;
  oldValue: string | null;
  newValue: string | null;
}

export type AuditPage = PageResult<AuditSummary>;

export interface AuditQuery {
  from: string;
  to: string;
  user: string;
  module: string;
  action: string;
  entity: string;
  search: string;
  page: number;
}

export const AUDIT_MODULES = ['AUTH', 'SALES', 'PURCHASE', 'INVENTORY', 'FINANCE', 'MASTER', 'PARTY', 'IDENTITY', 'SETTINGS', 'SYSTEM'] as const;

export const AUDIT_ACTIONS = [
  'login',
  'logout',
  'create',
  'update',
  'delete',
  'return',
  'confirm',
  'cancel',
  'settings',
  'STOCK_ADJUSTED',
  'STOCK_TRANSFER_CONFIRMED',
  'STOCK_TRANSFER_COMPLETED',
  'STOCK_TRANSFER_CANCELLED',
] as const;

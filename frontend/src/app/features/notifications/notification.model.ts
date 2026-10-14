import { PageResult } from '../../core/models/identity.model';

export interface AppNotification {
  id: number;
  kind: 'LOW_STOCK' | 'OUT_OF_STOCK' | 'FAILED_TRANSACTION' | 'SYSTEM';
  severity: 'INFO' | 'WARNING' | 'CRITICAL';
  title: string;
  message: string;
  module: string | null;
  entity: string | null;
  entityId: string | null;
  readAt: string | null;
  createdAt: string;
}

export interface NotificationSummary {
  unread: number;
}

export type NotificationPage = PageResult<AppNotification>;

export const NOTIFICATION_KINDS = [
  { id: '', label: 'All' },
  { id: 'LOW_STOCK', label: 'Low stock' },
  { id: 'OUT_OF_STOCK', label: 'Out of stock' },
  { id: 'FAILED_TRANSACTION', label: 'Failed transactions' },
  { id: 'SYSTEM', label: 'System' },
] as const;

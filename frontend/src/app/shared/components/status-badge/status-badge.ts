import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

const LABELS: Record<string, string> = {
  PAID: 'Paid',
  PARTIALLY_PAID: 'Partially Paid',
  UNPAID: 'Unpaid',
  COMPLETED: 'Completed',
  PENDING: 'Pending',
  CANCELLED: 'Cancelled',
  ACTIVE: 'Active',
  INACTIVE: 'Inactive',
  LOW_STOCK: 'Low Stock',
  OUT_OF_STOCK: 'Out of Stock',
};

const ICONS: Record<string, string> = {
  PAID: 'paid',
  PARTIALLY_PAID: 'timelapse',
  UNPAID: 'money_off',
  COMPLETED: 'task_alt',
  PENDING: 'schedule',
  CANCELLED: 'cancel',
  ACTIVE: 'check_circle',
  INACTIVE: 'block',
  LOW_STOCK: 'inventory',
  OUT_OF_STOCK: 'remove_shopping_cart',
  CONFIRMED: 'verified',
  DRAFT: 'edit_note',
  POSTED: 'check_circle',
  SETTLED: 'task_alt',
  OVERDUE: 'event_busy',
  LOCKED: 'lock',
  OPEN: 'hourglass_top',
  IN_STOCK: 'inventory_2',
  INCREASE: 'arrow_upward',
  DECREASE: 'arrow_downward',
};

@Component({
  selector: 'app-status-badge',
  imports: [MatIconModule],
  template: `
    <span [class]="'bf-badge ' + tone()" role="status">
      <mat-icon aria-hidden="true">{{ icon() }}</mat-icon>
      {{ label() }}
    </span>
  `,
  styleUrl: './status-badge.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StatusBadge {
  readonly status = input.required<string>();

  readonly label = computed(() => humanStatus(this.status()));
  readonly tone = computed(() => toneFor(this.status()));
  readonly icon = computed(() => iconFor(this.status(), this.tone()));
}

export function humanStatus(status: string): string {
  const value = status.toUpperCase();
  return LABELS[value] ?? titleCase(status.replaceAll('_', ' '));
}

function titleCase(value: string): string {
  return value
    .toLowerCase()
    .split(' ')
    .filter((part) => part.length > 0)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ');
}

function iconFor(status: string, tone: string): string {
  const value = status.toUpperCase();
  if (ICONS[value]) {
    return ICONS[value];
  }
  if (tone === 'is-success') {
    return 'check_circle';
  }
  if (tone === 'is-danger') {
    return 'error';
  }
  if (tone === 'is-warning') {
    return 'schedule';
  }
  if (tone === 'is-info') {
    return 'info';
  }
  return 'label';
}

function toneFor(status: string): string {
  const value = status.toUpperCase();
  if (
    value === 'UP' ||
    value === 'SUCCESS' ||
    value === 'ACTIVE' ||
    value === 'CONFIRMED' ||
    value === 'COMPLETED' ||
    value === 'POSTED' ||
    value === 'SETTLED' ||
    value === 'PAID' ||
    value === 'IN_STOCK' ||
    value === 'INCREASE' ||
    value === 'OPENING_STOCK' ||
    value === 'PURCHASE' ||
    value === 'SALES_RETURN' ||
    value === 'STOCK_TRANSFER_IN'
  ) {
    return 'is-success';
  }
  if (
    value === 'DOWN' ||
    value === 'FAILURE' ||
    value === 'ERROR' ||
    value === 'INACTIVE' ||
    value === 'CANCELLED' ||
    value === 'OVERDUE' ||
    value === 'UNPAID' ||
    value === 'OUT_OF_STOCK' ||
    value === 'DECREASE'
  ) {
    return 'is-danger';
  }
  if (
    value === 'WARNING' ||
    value === 'PENDING' ||
    value === 'LOCKED' ||
    value === 'DRAFT' ||
    value === 'OPEN' ||
    value === 'PARTIALLY_PAID' ||
    value === 'PARTIALLY_RETURNED' ||
    value === 'LOW_STOCK' ||
    value === 'STOCK_ADJUSTMENT' ||
    value === 'PURCHASE_RETURN' ||
    value === 'SALE'
  ) {
    return 'is-warning';
  }
  if (
    value === 'INFO' ||
    value === 'RETURNED' ||
    value === 'CREDIT' ||
    value === 'OVER_STOCK' ||
    value === 'STOCK_TRANSFER_OUT' ||
    value === 'PURCHASE_REVERSAL' ||
    value === 'SALE_REVERSAL'
  ) {
    return 'is-info';
  }
  return 'is-neutral';
}

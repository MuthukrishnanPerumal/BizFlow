import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';

@Component({
  selector: 'app-inventory-nav',
  imports: [RouterLink, RouterLinkActive, HasPermissionDirective],
  template: `
    <nav class="inv-nav" aria-label="Inventory sections">
      @for (link of links; track link.path) {
        <a
          *appHasPermission="link.permission"
          [routerLink]="link.path"
          routerLinkActive="is-active"
          [routerLinkActiveOptions]="{ exact: link.exact }"
        >
          {{ link.label }}
        </a>
      }
    </nav>
  `,
  styleUrl: './inventory-screen.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InventoryNav {
  readonly links = [
    { label: 'Dashboard', path: '/inventory', exact: true, permission: 'INVENTORY_VIEW' },
    { label: 'Overview', path: '/inventory/overview', exact: true, permission: 'INVENTORY_VIEW' },
    { label: 'Ledger', path: '/inventory/ledger', exact: true, permission: 'INVENTORY_VIEW' },
    { label: 'Low stock', path: '/inventory/low-stock', exact: true, permission: 'INVENTORY_VIEW' },
    { label: 'Out of stock', path: '/inventory/out-of-stock', exact: true, permission: 'INVENTORY_VIEW' },
    { label: 'Valuation', path: '/inventory/valuation', exact: true, permission: 'INVENTORY_VIEW' },
    { label: 'Adjustments', path: '/stock-adjustment', exact: false, permission: 'INVENTORY_ADJUST' },
    { label: 'Transfers', path: '/stock-transfer', exact: false, permission: 'INVENTORY_TRANSFER' },
  ];
}

import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';

@Component({
  selector: 'app-finance-nav',
  imports: [RouterLink, RouterLinkActive, HasPermissionDirective],
  template: `
    <nav class="fin-nav" aria-label="Finance sections">
      @for (link of links; track link.path) {
        <a *appHasPermission="link.permission" [routerLink]="link.path" routerLinkActive="is-active">
          {{ link.label }}
        </a>
      }
    </nav>
  `,
  styleUrl: './finance-screen.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FinanceNav {
  readonly links = [
    { label: 'Customer payments', path: '/payments/customer', permission: 'PAYMENT_VIEW' },
    { label: 'Supplier payments', path: '/payments/supplier', permission: 'PAYMENT_VIEW' },
    { label: 'Expenses', path: '/expenses', permission: 'EXPENSE_VIEW' },
    { label: 'Receivables', path: '/receivables', permission: 'RECEIVABLE_VIEW' },
    { label: 'Payables', path: '/payables', permission: 'PAYABLE_VIEW' },
  ];
}

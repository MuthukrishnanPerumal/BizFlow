import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';

import { PageHeader } from '../../shared/components/page-header/page-header';

@Component({
  selector: 'app-reports-home-page',
  imports: [RouterLink, MatIconModule, PageHeader],
  templateUrl: './reports-home-page.html',
  styleUrl: './reports-home-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReportsHomePage {
  readonly reports = [
    {
      title: 'Sales',
      path: '/reports/sales',
      icon: 'receipt',
      detail: 'Invoices, discounts, tax, collections, and open balances.',
    },
    {
      title: 'Purchases',
      path: '/reports/purchases',
      icon: 'receipt_long',
      detail: 'Supplier bills, discounts, tax, payments, and open balances.',
    },
    {
      title: 'Inventory',
      path: '/reports/inventory',
      icon: 'inventory_2',
      detail: 'Opening stock, movement, closing stock, and stock value.',
    },
    {
      title: 'Profit',
      path: '/reports/profit',
      icon: 'monitoring',
      detail: 'Revenue, cost of goods sold, expenses, gross profit, and net profit.',
    },
  ];
}

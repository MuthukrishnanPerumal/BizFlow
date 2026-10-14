import { Injectable, inject } from '@angular/core';
import { Observable, catchError, forkJoin, map, of } from 'rxjs';

import { AuthSession } from '../../core/auth/auth-session.service';
import { NAVIGATION, visibleNavigation } from '../../core/constants/navigation';
import { NavNode } from '../../core/models/navigation.model';
import { PartyApi } from '../../features/master/party-api.service';
import { ProductApi } from '../../features/products/product-api.service';
import { PurchaseApi } from '../../features/purchases/purchase-api.service';
import { SaleApi } from '../../features/sales/sale-api.service';

export interface CommandHit {
  id: string;
  kind: 'Page' | 'Action' | 'Product' | 'Customer' | 'Supplier' | 'Invoice';
  title: string;
  detail: string;
  route: string;
}

export interface CommandResults {
  hits: CommandHit[];
  pending: boolean;
  failed: boolean;
}

interface CommandAction {
  label: string;
  detail: string;
  route: string;
  permission: string;
  keywords: string;
}

const ACTIONS: readonly CommandAction[] = [
  { label: 'New sale', detail: 'Create a sales invoice', route: '/sales/new', permission: 'SALES_CREATE', keywords: 'invoice bill' },
  { label: 'New sales return', detail: 'Return items from a sale', route: '/sales-returns/new', permission: 'SALES_CREATE', keywords: 'refund' },
  { label: 'New purchase', detail: 'Record a supplier bill', route: '/purchases/new', permission: 'PURCHASE_CREATE', keywords: 'bill' },
  { label: 'New product', detail: 'Add a product', route: '/products/new', permission: 'PRODUCT_CREATE', keywords: 'item sku' },
  { label: 'New customer', detail: 'Add a customer', route: '/customers/new', permission: 'CUSTOMER_CREATE', keywords: 'party' },
  { label: 'New supplier', detail: 'Add a supplier', route: '/suppliers/new', permission: 'SUPPLIER_CREATE', keywords: 'vendor party' },
  { label: 'New expense', detail: 'Record an expense', route: '/expenses/new', permission: 'EXPENSE_CREATE', keywords: 'cost' },
  { label: 'Receive payment', detail: 'Customer receipt', route: '/payments/customer/new', permission: 'PAYMENT_CREATE', keywords: 'receipt' },
  { label: 'Pay supplier', detail: 'Supplier payment', route: '/payments/supplier/new', permission: 'PAYMENT_CREATE', keywords: 'payment voucher' },
  { label: 'Adjust stock', detail: 'Increase or decrease stock', route: '/stock-adjustment/new', permission: 'INVENTORY_ADJUST', keywords: 'adjustment' },
  { label: 'Transfer stock', detail: 'Move stock between locations', route: '/stock-transfer/new', permission: 'INVENTORY_TRANSFER', keywords: 'transfer' },
];

@Injectable({ providedIn: 'root' })
export class CommandSearch {
  private readonly session = inject(AuthSession);
  private readonly products = inject(ProductApi);
  private readonly parties = inject(PartyApi);
  private readonly sales = inject(SaleApi);
  private readonly purchases = inject(PurchaseApi);

  search(raw: string): Observable<CommandResults> {
    const term = raw.trim().toLowerCase();
    const local = [...this.pages(term), ...this.actions(term)];
    if (term.length < 2) {
      return of({ hits: local, pending: false, failed: false });
    }
    return forkJoin({
      products: this.productHits(term),
      customers: this.partyHits('customers', 'Customer', term),
      suppliers: this.partyHits('suppliers', 'Supplier', term),
      sales: this.saleHits(term),
      purchases: this.purchaseHits(term),
    }).pipe(
      map((records) => {
        const failed = Object.values(records).some((entry) => entry.failed);
        const hits = [
          ...local,
          ...records.products.hits,
          ...records.customers.hits,
          ...records.suppliers.hits,
          ...records.sales.hits,
          ...records.purchases.hits,
        ];
        return { hits, pending: false, failed };
      }),
    );
  }

  private pages(term: string): CommandHit[] {
    return flattenPages(visibleNavigation(NAVIGATION, (permission) => this.session.hasPermission(permission)))
      .filter((page) => term.length === 0 || matches(term, page.title, page.detail))
      .slice(0, 8)
      .map((page) => ({ ...page, kind: 'Page' as const }));
  }

  private actions(term: string): CommandHit[] {
    return ACTIONS.filter((action) => this.session.hasPermission(action.permission))
      .filter((action) => term.length === 0 || matches(term, action.label, action.detail, action.keywords))
      .slice(0, 6)
      .map((action) => ({
        id: `action:${action.route}`,
        kind: 'Action' as const,
        title: action.label,
        detail: action.detail,
        route: action.route,
      }));
  }

  private productHits(term: string): Observable<{ hits: CommandHit[]; failed: boolean }> {
    if (!this.session.hasPermission('PRODUCT_VIEW')) {
      return of({ hits: [], failed: false });
    }
    return this.products.list(term, '', '', null, 0, 'name,asc', 5).pipe(
      map((page) => ({
        failed: false,
        hits: page.items.map((product) => ({
          id: `product:${product.id}`,
          kind: 'Product' as const,
          title: product.name,
          detail: product.productCode,
          route: `/products/${product.id}`,
        })),
      })),
      catchError(() => of({ hits: [], failed: true })),
    );
  }

  private partyHits(
    resource: 'customers' | 'suppliers',
    kind: 'Customer' | 'Supplier',
    term: string,
  ): Observable<{ hits: CommandHit[]; failed: boolean }> {
    const permission = resource === 'customers' ? 'CUSTOMER_VIEW' : 'SUPPLIER_VIEW';
    if (!this.session.hasPermission(permission)) {
      return of({ hits: [], failed: false });
    }
    const sort = resource === 'customers' ? 'customerName,asc' : 'supplierName,asc';
    return this.parties.list(resource, term, '', 0, sort, 5).pipe(
      map((page) => ({
        failed: false,
        hits: page.items.map((party) => ({
          id: `${resource}:${party.id}`,
          kind,
          title: (resource === 'customers' ? party.customerName : party.supplierName) || party.companyName || 'Unnamed',
          detail: (resource === 'customers' ? party.customerCode : party.supplierCode) || party.phone || '',
          route: `/${resource}/${party.id}`,
        })),
      })),
      catchError(() => of({ hits: [], failed: true })),
    );
  }

  private saleHits(term: string): Observable<{ hits: CommandHit[]; failed: boolean }> {
    if (!this.session.hasPermission('SALES_VIEW')) {
      return of({ hits: [], failed: false });
    }
    return this.sales
      .list({
        search: term,
        status: '',
        paymentType: '',
        customerId: null,
        invoiceNumber: '',
        dateFrom: '',
        dateTo: '',
        page: 0,
        sort: 'invoiceDate,desc',
      })
      .pipe(
        map((page) => ({
          failed: false,
          hits: page.items.slice(0, 5).map((sale) => ({
            id: `sale:${sale.id}`,
            kind: 'Invoice' as const,
            title: sale.invoiceNumber,
            detail: `Sale · ${sale.customerName}`,
            route: `/sales/${sale.id}`,
          })),
        })),
        catchError(() => of({ hits: [], failed: true })),
      );
  }

  private purchaseHits(term: string): Observable<{ hits: CommandHit[]; failed: boolean }> {
    if (!this.session.hasPermission('PURCHASE_VIEW')) {
      return of({ hits: [], failed: false });
    }
    return this.purchases
      .list({
        search: term,
        status: '',
        supplierId: null,
        invoiceNumber: '',
        dateFrom: '',
        dateTo: '',
        page: 0,
        sort: 'purchaseDate,desc',
      })
      .pipe(
        map((page) => ({
          failed: false,
          hits: page.items.slice(0, 5).map((purchase) => ({
            id: `purchase:${purchase.id}`,
            kind: 'Invoice' as const,
            title: purchase.invoiceNumber || purchase.purchaseNumber,
            detail: `Purchase · ${purchase.supplierName}`,
            route: `/purchases/${purchase.id}`,
          })),
        })),
        catchError(() => of({ hits: [], failed: true })),
      );
  }
}

function flattenPages(nodes: readonly NavNode[], group = ''): CommandHit[] {
  const pages: CommandHit[] = [];
  for (const node of nodes) {
    if (node.route) {
      pages.push({
        id: `page:${node.route}`,
        kind: 'Page',
        title: node.label,
        detail: group,
        route: node.route,
      });
    }
    if (node.children) {
      pages.push(...flattenPages(node.children, node.label));
    }
  }
  return pages;
}

function matches(term: string, ...fields: string[]): boolean {
  const haystack = fields.join(' ').toLowerCase();
  return term.split(/\s+/).every((part) => haystack.includes(part));
}

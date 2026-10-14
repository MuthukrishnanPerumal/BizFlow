import { NavNode, Crumb } from '../models/navigation.model';

export const NAVIGATION: readonly NavNode[] = [
  { label: 'Dashboard', icon: 'dashboard', route: '/dashboard' },
  {
    label: 'Sales',
    icon: 'point_of_sale',
    children: [
      { label: 'Sales', icon: 'receipt', route: '/sales', permission: 'SALES_VIEW' },
      { label: 'Sales returns', icon: 'undo', route: '/sales-returns', permission: 'SALES_VIEW' },
    ],
  },
  {
    label: 'Purchases',
    icon: 'shopping_cart',
    children: [
      { label: 'Purchases', icon: 'receipt_long', route: '/purchases', permission: 'PURCHASE_VIEW' },
      { label: 'Purchase returns', icon: 'assignment_return', route: '/purchase-returns', permission: 'PURCHASE_VIEW' },
    ],
  },
  {
    label: 'Inventory',
    icon: 'warehouse',
    children: [
      { label: 'Stock health', icon: 'monitor_heart', route: '/inventory', permission: 'INVENTORY_VIEW' },
      { label: 'Stock overview', icon: 'inventory', route: '/inventory/overview', permission: 'INVENTORY_VIEW' },
      { label: 'Stock ledger', icon: 'menu_book', route: '/inventory/ledger', permission: 'INVENTORY_VIEW' },
      { label: 'Low stock', icon: 'warning', route: '/inventory/low-stock', permission: 'INVENTORY_VIEW' },
      { label: 'Out of stock', icon: 'remove_shopping_cart', route: '/inventory/out-of-stock', permission: 'INVENTORY_VIEW' },
      { label: 'Valuation', icon: 'payments', route: '/inventory/valuation', permission: 'INVENTORY_VIEW' },
      { label: 'Stock adjustment', icon: 'tune', route: '/stock-adjustment', permission: 'INVENTORY_ADJUST' },
      { label: 'Stock transfer', icon: 'swap_horiz', route: '/stock-transfer', permission: 'INVENTORY_TRANSFER' },
    ],
  },
  {
    label: 'Masters',
    icon: 'category',
    children: [
      { label: 'Products', icon: 'inventory_2', route: '/products', permission: 'PRODUCT_VIEW' },
      { label: 'Categories', icon: 'label', route: '/categories', permission: 'PRODUCT_VIEW' },
      { label: 'Brands', icon: 'sell', route: '/brands', permission: 'PRODUCT_VIEW' },
      { label: 'Units', icon: 'straighten', route: '/units', permission: 'PRODUCT_VIEW' },
      { label: 'Suppliers', icon: 'local_shipping', route: '/suppliers', permission: 'SUPPLIER_VIEW' },
      { label: 'Customers', icon: 'person', route: '/customers', permission: 'CUSTOMER_VIEW' },
    ],
  },
  {
    label: 'Finance',
    icon: 'account_balance',
    children: [
      { label: 'Customer payments', icon: 'payments', route: '/payments/customer', permission: 'PAYMENT_VIEW' },
      { label: 'Supplier payments', icon: 'account_balance_wallet', route: '/payments/supplier', permission: 'PAYMENT_VIEW' },
      { label: 'Expenses', icon: 'request_quote', route: '/expenses', permission: 'EXPENSE_VIEW' },
      { label: 'Receivables', icon: 'south_west', route: '/receivables', permission: 'RECEIVABLE_VIEW' },
      { label: 'Payables', icon: 'north_east', route: '/payables', permission: 'PAYABLE_VIEW' },
    ],
  },
  {
    label: 'Reports',
    icon: 'assessment',
    children: [
      { label: 'All reports', icon: 'assessment', route: '/reports', permission: 'REPORT_VIEW' },
      { label: 'Sales report', icon: 'receipt', route: '/reports/sales', permission: 'REPORT_VIEW' },
      { label: 'Purchase report', icon: 'receipt_long', route: '/reports/purchases', permission: 'REPORT_VIEW' },
      { label: 'Inventory report', icon: 'inventory_2', route: '/reports/inventory', permission: 'REPORT_VIEW' },
      { label: 'Profit report', icon: 'monitoring', route: '/reports/profit', permission: 'REPORT_VIEW' },
    ],
  },
  {
    label: 'Administration',
    icon: 'admin_panel_settings',
    children: [
      { label: 'Users', icon: 'manage_accounts', route: '/users', permission: 'USER_VIEW' },
      { label: 'Roles', icon: 'badge', route: '/roles', permission: 'ROLE_VIEW' },
      { label: 'Permissions', icon: 'key', route: '/permissions', permission: 'ROLE_VIEW' },
      { label: 'Notifications', icon: 'notifications', route: '/notifications', permission: 'NOTIFICATION_VIEW' },
      { label: 'Audit logs', icon: 'history', route: '/audit-logs', permission: 'AUDIT_VIEW' },
      { label: 'Settings', icon: 'settings', route: '/settings', permission: 'SETTINGS_VIEW' },
    ],
  },
];

export function pathOf(url: string): string {
  const path = url.split('?')[0]?.split('#')[0] ?? '';
  return path.length > 0 ? path : '/';
}

export function matchesRoute(path: string, route: string): boolean {
  return path === route || path.startsWith(`${route}/`);
}

export function collectRoutes(nodes: readonly NavNode[]): string[] {
  const routes: string[] = [];
  for (const node of nodes) {
    if (node.route) {
      routes.push(node.route);
    }
    if (node.children) {
      routes.push(...collectRoutes(node.children));
    }
  }
  return routes;
}

/** Longest nav route that contains the path, so /inventory does not stay active on /inventory/overview. */
export function activeRoute(path: string, routes: readonly string[]): string | null {
  let best: string | null = null;
  for (const route of routes) {
    if (matchesRoute(path, route) && (best === null || route.length > best.length)) {
      best = route;
    }
  }
  return best;
}

export function visibleNavigation(nodes: readonly NavNode[], allowed: (permission: string) => boolean): NavNode[] {
  const visible: NavNode[] = [];
  for (const node of nodes) {
    if (node.children) {
      const children = node.children.filter((child) => !child.permission || allowed(child.permission));
      if (children.length > 0) {
        visible.push({ ...node, children });
      }
      continue;
    }
    if (!node.permission || allowed(node.permission)) {
      visible.push(node);
    }
  }
  return visible;
}

export function navigationCrumbs(path: string, nodes: readonly NavNode[]): Crumb[] {
  let match: { group: NavNode | null; node: NavNode } | null = null;
  for (const node of nodes) {
    if (node.route && matchesRoute(path, node.route) && node.route.length > (match?.node.route?.length ?? 0)) {
      match = { group: null, node };
    }
    for (const child of node.children ?? []) {
      if (child.route && matchesRoute(path, child.route) && child.route.length > (match?.node.route?.length ?? 0)) {
        match = { group: node, node: child };
      }
    }
  }
  if (!match?.node.route) {
    return [];
  }

  const crumbs: Crumb[] = [];
  const home = sectionHome(match.group);
  if (match.group && home && home !== match.node.route) {
    crumbs.push({ label: match.group.label, url: home, link: path !== home });
  }
  crumbs.push({
    label: match.node.label,
    url: match.node.route,
    link: path !== match.node.route,
  });
  return crumbs;
}

function sectionHome(group: NavNode | null): string | null {
  const routes = (group?.children ?? []).map((child) => child.route).filter((route): route is string => !!route);
  return routes.sort((left, right) => left.length - right.length)[0] ?? null;
}

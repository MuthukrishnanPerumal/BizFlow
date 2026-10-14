import { routes } from '../../app.routes';
import {
  NAVIGATION,
  activeRoute,
  collectRoutes,
  navigationCrumbs,
  pathOf,
  visibleNavigation,
} from './navigation';

describe('shell navigation', () => {
  it('keeps the longest matching route active', () => {
    const routesInMenu = collectRoutes(NAVIGATION);
    expect(activeRoute('/inventory/overview', routesInMenu)).toBe('/inventory/overview');
    expect(activeRoute('/inventory/products/4', routesInMenu)).toBe('/inventory');
    expect(activeRoute('/reports/sales', routesInMenu)).toBe('/reports/sales');
    expect(activeRoute('/sales/15/edit', routesInMenu)).toBe('/sales');
    expect(activeRoute('/sales-returns', routesInMenu)).toBe('/sales-returns');
  });

  it('hides items the role cannot open', () => {
    const visible = visibleNavigation(NAVIGATION, (permission) => permission === 'SALES_VIEW');
    const labels = visible.map((item) => item.label);
    expect(labels).toContain('Sales');
    expect(labels).not.toContain('Purchases');
    expect(labels).toContain('Dashboard');
  });

  it('builds a parent trail without duplicating the current page', () => {
    expect(navigationCrumbs('/sales/new', NAVIGATION)).toEqual([
      { label: 'Sales', url: '/sales', link: true },
    ]);
    expect(navigationCrumbs('/inventory/overview', NAVIGATION)).toEqual([
      { label: 'Inventory', url: '/inventory', link: true },
      { label: 'Stock overview', url: '/inventory/overview', link: false },
    ]);
    expect(pathOf('/sales/new?x=1')).toBe('/sales/new');
  });

  it('points every menu route at a registered shell path', () => {
    const shell = routes.find((route) => route.path === '' && route.children);
    const registered = new Set((shell?.children ?? []).map((route) => route.path));
    for (const route of collectRoutes(NAVIGATION)) {
      const segment = route.split('/').filter((part) => part.length > 0)[0];
      expect(registered.has(segment)).toBe(true);
    }
  });
});

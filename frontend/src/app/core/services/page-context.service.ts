import { Injectable, inject, signal } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { ActivatedRouteSnapshot, NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs';

import { NAVIGATION, navigationCrumbs, pathOf } from '../constants/navigation';
import { Crumb } from '../models/navigation.model';

@Injectable({ providedIn: 'root' })
export class PageContext {
  private readonly router = inject(Router);
  private readonly documentTitle = inject(Title);

  readonly title = signal('Dashboard');
  readonly crumbs = signal<readonly Crumb[]>([]);

  constructor() {
    this.router.events.pipe(filter((event) => event instanceof NavigationEnd)).subscribe(() => {
      this.sync();
    });
    this.sync();
  }

  private sync(): void {
    const fromRoute: Crumb[] = [];
    let route: ActivatedRouteSnapshot | null = this.router.routerState.snapshot.root;
    let url = '';
    let pageTitle = 'Aveline BizFlow';

    while (route) {
      const segment = route.url.map((part) => part.path).join('/');
      if (segment.length > 0) {
        url = `${url}/${segment}`;
      }
      const breadcrumb = route.data['breadcrumb'];
      const routeTitle = route.data['title'];
      if (typeof breadcrumb === 'string' && breadcrumb.length > 0) {
        fromRoute.push({ label: breadcrumb, url: url.length > 0 ? url : '/' });
      }
      if (typeof routeTitle === 'string' && routeTitle.length > 0) {
        pageTitle = routeTitle;
      }
      route = route.firstChild;
    }

    const path = pathOf(this.router.url);
    const fromNav = navigationCrumbs(path, NAVIGATION);
    const extras = fromRoute.filter((crumb) => !fromNav.some((item) => item.url === crumb.url));
    this.title.set(pageTitle);
    this.crumbs.set(fromNav.length > 0 ? [...fromNav, ...extras] : fromRoute);
    this.documentTitle.setTitle(`${pageTitle} · Aveline BizFlow`);
  }
}

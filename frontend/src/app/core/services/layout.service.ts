import { BreakpointObserver } from '@angular/cdk/layout';
import { Injectable, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs';

import { STORAGE_KEYS } from '../constants/storage-keys';

@Injectable({ providedIn: 'root' })
export class LayoutService {
  private readonly breakpoints = inject(BreakpointObserver);

  /** Phone and tablet use an overlay drawer. Laptop and desktop keep a docked sidebar. */
  readonly mobile = toSignal(
    this.breakpoints.observe('(max-width: 959px)').pipe(map((result) => result.matches)),
    { initialValue: false },
  );

  readonly collapsed = signal(this.readCollapsed());
  readonly mobileOpen = signal(false);

  readonly sidenavMode = computed(() => (this.mobile() ? 'over' : 'side'));
  readonly sidenavOpened = computed(() => (this.mobile() ? this.mobileOpen() : true));
  readonly showLabels = computed(() => this.mobile() || !this.collapsed());

  toggleNavigation(): void {
    if (this.mobile()) {
      this.mobileOpen.update((open) => !open);
      return;
    }
    this.collapsed.update((collapsed) => {
      const next = !collapsed;
      this.writeCollapsed(next);
      return next;
    });
  }

  closeMobile(): void {
    this.mobileOpen.set(false);
  }

  expand(): void {
    this.collapsed.set(false);
    this.writeCollapsed(false);
  }

  private readCollapsed(): boolean {
    try {
      const stored = localStorage.getItem(STORAGE_KEYS.sidebarCollapsed);
      if (stored === 'true') {
        return true;
      }
      if (stored === 'false') {
        return false;
      }
    } catch {
      return false;
    }
    return typeof window !== 'undefined' && window.matchMedia('(max-width: 1199px)').matches;
  }

  private writeCollapsed(collapsed: boolean): void {
    try {
      localStorage.setItem(STORAGE_KEYS.sidebarCollapsed, String(collapsed));
    } catch {
      // Ignore storage failures.
    }
  }
}

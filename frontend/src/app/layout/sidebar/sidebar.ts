import { Overlay, OverlayRef } from '@angular/cdk/overlay';
import { TemplatePortal } from '@angular/cdk/portal';
import {
  ChangeDetectionStrategy,
  Component,
  HostListener,
  TemplateRef,
  ViewChild,
  ViewContainerRef,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { NavigationEnd, Router, RouterLink } from '@angular/router';
import { filter, map, startWith, Subscription } from 'rxjs';

import { AuthSession } from '../../core/auth/auth-session.service';
import { NAVIGATION, activeRoute, collectRoutes, pathOf, visibleNavigation } from '../../core/constants/navigation';
import { NavNode } from '../../core/models/navigation.model';
import { LayoutService } from '../../core/services/layout.service';
import { SystemSnapshot } from '../../core/services/system-snapshot.service';

@Component({
  selector: 'app-sidebar',
  imports: [RouterLink, MatIconModule, MatTooltipModule],
  templateUrl: './sidebar.html',
  styleUrl: './sidebar.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Sidebar {
  readonly layout = inject(LayoutService);
  readonly snapshot = inject(SystemSnapshot);
  private readonly session = inject(AuthSession);
  private readonly router = inject(Router);
  private readonly overlay = inject(Overlay);
  private readonly view = inject(ViewContainerRef);
  private overlayRef: OverlayRef | null = null;
  private backdropSub: Subscription | null = null;
  private readonly opened = signal<ReadonlySet<string>>(new Set());
  private readonly closed = signal<ReadonlySet<string>>(new Set());
  readonly flyout = signal<NavNode | null>(null);

  @ViewChild('flyoutMenu') private flyoutMenu?: TemplateRef<unknown>;

  private readonly url = toSignal(
    this.router.events.pipe(
      filter((event) => event instanceof NavigationEnd),
      map(() => this.router.url),
      startWith(this.router.url),
    ),
    { initialValue: this.router.url },
  );

  readonly navigation = computed(() => {
    const user = this.session.user();
    if (user?.mustChangePassword) {
      return [] as NavNode[];
    }
    return visibleNavigation(NAVIGATION, (permission) => this.session.hasPermission(permission));
  });

  readonly brand = computed(() => this.snapshot.preferences()?.displayName ?? 'Aveline BizFlow');

  constructor() {
    this.router.events
      .pipe(
        filter((event) => event instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe(() => {
        this.layout.closeMobile();
        this.closeFlyout();
        this.revealActiveGroup();
      });
  }

  isOpen(node: NavNode): boolean {
    this.url();
    if (this.closed().has(node.label)) {
      return false;
    }
    if (this.opened().has(node.label)) {
      return true;
    }
    return this.containsCurrent(node);
  }

  containsCurrent(node: NavNode): boolean {
    return (node.children ?? []).some((child) => !!child.route && this.isActive(child.route));
  }

  isActive(route: string): boolean {
    this.url();
    return activeRoute(pathOf(this.router.url), collectRoutes(this.navigation())) === route;
  }

  toggle(node: NavNode, event: Event): void {
    if (!this.layout.showLabels()) {
      this.openFlyout(node, event);
      return;
    }
    if (this.isOpen(node)) {
      this.closed.update((current) => new Set(current).add(node.label));
      this.opened.update((current) => {
        const next = new Set(current);
        next.delete(node.label);
        return next;
      });
      return;
    }
    this.opened.update((current) => new Set(current).add(node.label));
    this.closed.update((current) => {
      const next = new Set(current);
      next.delete(node.label);
      return next;
    });
  }

  @HostListener('document:keydown.escape')
  closeFlyout(): void {
    this.backdropSub?.unsubscribe();
    this.backdropSub = null;
    this.flyout.set(null);
    this.overlayRef?.dispose();
    this.overlayRef = null;
  }

  private openFlyout(node: NavNode, event: Event): void {
    if (this.flyout()?.label === node.label) {
      this.closeFlyout();
      return;
    }
    this.closeFlyout();
    this.flyout.set(node);
    const target = event.currentTarget;
    const menu = this.flyoutMenu;
    if (!(target instanceof HTMLElement) || !menu) {
      return;
    }
    this.overlayRef = this.overlay.create({
      hasBackdrop: true,
      backdropClass: 'bf-flyout-backdrop',
      positionStrategy: this.overlay
        .position()
        .flexibleConnectedTo(target)
        .withPositions([{ originX: 'end', originY: 'top', overlayX: 'start', overlayY: 'top', offsetX: 8 }]),
      scrollStrategy: this.overlay.scrollStrategies.reposition(),
    });
    this.overlayRef.attach(new TemplatePortal(menu, this.view));
    this.backdropSub = this.overlayRef.backdropClick().subscribe(() => this.closeFlyout());
  }

  private revealActiveGroup(): void {
    const current = this.navigation().find((item) => this.containsCurrent(item));
    if (!current) {
      return;
    }
    this.closed.update((set) => {
      const next = new Set(set);
      next.delete(current.label);
      return next;
    });
  }
}

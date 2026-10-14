import { ChangeDetectionStrategy, Component, HostListener, computed, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { Router, RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';
import { ConfirmationService } from '../../core/services/confirmation.service';
import { LayoutService } from '../../core/services/layout.service';
import { PageContext } from '../../core/services/page-context.service';
import { InboxStore } from '../../features/notifications/inbox.store';
import { CommandPaletteService } from '../command-palette/command-palette.service';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { StatusBadge } from '../../shared/components/status-badge/status-badge';

@Component({
  selector: 'app-header',
  imports: [MatButtonModule, MatIconModule, MatMenuModule, RouterLink, HasPermissionDirective, StatusBadge],
  templateUrl: './header.html',
  styleUrl: './header.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Header {
  readonly layout = inject(LayoutService);
  readonly page = inject(PageContext);
  readonly auth = inject(AuthService);
  readonly inbox = inject(InboxStore);
  private readonly confirmation = inject(ConfirmationService);
  private readonly router = inject(Router);
  private readonly palette = inject(CommandPaletteService);

  readonly locked = computed(() => this.auth.user()?.mustChangePassword === true);
  readonly initials = computed(() => initialsOf(this.displayName()));
  readonly roleLabel = computed(() => {
    const user = this.auth.user();
    if (!user) {
      return 'Signed out';
    }
    if (user.mustChangePassword) {
      return 'Password required';
    }
    return user.roles[0] || user.status;
  });

  constructor() {
    this.inbox.loadSummary();
  }

  navLabel(): string {
    if (this.layout.mobile()) {
      return this.layout.mobileOpen() ? 'Close navigation' : 'Open navigation';
    }
    return this.layout.collapsed() ? 'Expand navigation' : 'Collapse navigation';
  }

  @HostListener('document:keydown', ['$event'])
  onSearchKey(event: KeyboardEvent): void {
    if (this.locked() || (!(event.ctrlKey || event.metaKey) || event.key.toLowerCase() !== 'k')) {
      return;
    }
    event.preventDefault();
    this.palette.toggle();
  }

  openSearch(): void {
    if (!this.locked()) {
      this.palette.toggle();
    }
  }

  notificationLabel(): string {
    const unread = this.inbox.unread();
    return unread > 0 ? `Notifications, ${unread} unread` : 'Notifications';
  }

  displayName(): string {
    const user = this.auth.user();
    if (!user) {
      return 'Not signed in';
    }
    return user.fullName || user.username;
  }

  signOut(): void {
    this.confirmation
      .confirm({
        title: 'Sign out',
        message: 'End this session in the current browser?',
        confirmLabel: 'Sign out',
        cancelLabel: 'Stay signed in',
        destructive: true,
      })
      .subscribe((confirmed) => {
        if (confirmed) {
          this.auth.logout().subscribe(() => {
            void this.router.navigate(['/login']);
          });
        }
      });
  }
}

function initialsOf(name: string): string {
  const parts = name.split(/\s+/).filter((part) => part.length > 0);
  const letters = `${parts[0]?.[0] ?? ''}${parts[1]?.[0] ?? ''}`;
  return letters.toUpperCase() || 'U';
}

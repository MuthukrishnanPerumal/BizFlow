import { ChangeDetectionStrategy, Component, HostListener, inject } from '@angular/core';
import { MatSidenavModule } from '@angular/material/sidenav';
import { RouterOutlet } from '@angular/router';

import { LayoutService } from '../../core/services/layout.service';
import { LoadingService } from '../../core/services/loading.service';
import { SystemSnapshot } from '../../core/services/system-snapshot.service';
import { LoadingIndicator } from '../../shared/components/loading-indicator/loading-indicator';
import { Footer } from '../footer/footer';
import { Header } from '../header/header';
import { Sidebar } from '../sidebar/sidebar';

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, MatSidenavModule, LoadingIndicator, Header, Sidebar, Footer],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Shell {
  readonly layout = inject(LayoutService);
  readonly loading = inject(LoadingService);
  private readonly snapshot = inject(SystemSnapshot);

  constructor() {
    this.snapshot.load();
  }

  onSidenavClosed(): void {
    if (this.layout.mobile()) {
      this.layout.closeMobile();
    }
  }

  @HostListener('click', ['$event'])
  onRowDetails(event: Event): void {
    const target = event.target;
    if (!(target instanceof Element)) {
      return;
    }
    const button = target.closest('button.row-details');
    if (!(button instanceof HTMLButtonElement)) {
      return;
    }
    const row = button.closest('tr');
    if (!row) {
      return;
    }
    const open = row.classList.toggle('is-expanded');
    button.setAttribute('aria-expanded', String(open));
  }
}

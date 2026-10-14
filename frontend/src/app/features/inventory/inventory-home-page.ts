import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { RouterLink } from '@angular/router';

import { readApiError } from '../../core/utils/http-error.util';
import { formatMoney, formatDate } from '../master/master-format';
import { ErrorState } from '../../shared/components/error-state/error-state';
import { FilterPanel } from '../../shared/components/filter-panel/filter-panel';
import { PageHeader } from '../../shared/components/page-header/page-header';
import { SkeletonLoader } from '../../shared/components/skeleton-loader/skeleton-loader';
import { StatusBadge } from '../../shared/components/status-badge/status-badge';
import { InventoryApi } from './inventory-api.service';
import { formatQty, label } from './inventory-format';
import { InventoryNav } from './inventory-nav';
import { InventoryDashboard, StockLocation } from './inventory.model';

@Component({
  selector: 'app-inventory-home-page',
  imports: [
    MatButtonModule,
    MatFormFieldModule,
    MatSelectModule,
    RouterLink,
    PageHeader,
    FilterPanel,
    ErrorState,
    SkeletonLoader,
    StatusBadge,
    InventoryNav,
  ],
  templateUrl: './inventory-home-page.html',
  styleUrls: ['../master/master-table.scss', '../../shared/components/data-table/data-table.scss', './inventory-screen.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InventoryHomePage {
  private readonly api = inject(InventoryApi);

  readonly money = formatMoney;
  readonly qty = formatQty;
  readonly date = formatDate;
  readonly label = label;
  readonly loading = signal(true);
  readonly failed = signal(false);
  readonly errorMessage = signal('');
  readonly locations = signal<StockLocation[]>([]);
  readonly locationId = signal<number | null>(null);
  readonly dashboard = signal<InventoryDashboard | null>(null);

  constructor() {
    this.api.locations().subscribe({
      next: (locations) => this.locations.set(locations),
      error: () => this.locations.set([]),
    });
    this.load();
  }

  onLocation(value: string): void {
    this.locationId.set(value ? Number(value) : null);
    this.load();
  }

  maxMoves(): number {
    const rows = this.dashboard()?.movement ?? [];
    return Math.max(1, ...rows.map((row) => row.movementCount));
  }

  load(): void {
    this.loading.set(true);
    this.failed.set(false);
    this.api.dashboard(this.locationId()).subscribe({
      next: (dashboard) => {
        this.dashboard.set(dashboard);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.loading.set(false);
        this.failed.set(true);
        this.errorMessage.set(readApiError(error));
      },
    });
  }
}

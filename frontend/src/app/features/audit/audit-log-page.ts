import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatMenuModule } from '@angular/material/menu';
import { MatSelectModule } from '@angular/material/select';

import { readApiError } from '../../core/utils/http-error.util';
import { formatWhen } from '../master/master-format';
import { IsoDateField } from '../../shared/components/iso-date-field/iso-date-field';
import { EmptyState } from '../../shared/components/empty-state/empty-state';
import { ErrorState } from '../../shared/components/error-state/error-state';
import { FilterPanel } from '../../shared/components/filter-panel/filter-panel';
import { PageHeader } from '../../shared/components/page-header/page-header';
import { Pagination } from '../../shared/components/pagination/pagination';
import { SearchBar } from '../../shared/components/search-bar/search-bar';
import { SkeletonLoader } from '../../shared/components/skeleton-loader/skeleton-loader';
import { StatusBadge } from '../../shared/components/status-badge/status-badge';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { AuditApi } from './audit-api.service';
import { AUDIT_ACTIONS, AUDIT_MODULES, AuditDetail, AuditQuery, AuditSummary } from './audit.model';

@Component({
  selector: 'app-audit-log-page',
  imports: [
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatMenuModule,
    MatSelectModule,
    PageHeader,
    IsoDateField,
    FilterPanel,
    SearchBar,
    Pagination,
    EmptyState,
    ErrorState,
    SkeletonLoader,
    StatusBadge,
    HasPermissionDirective,
  ],
  templateUrl: './audit-log-page.html',
  styleUrls: ['./audit-screen.scss', '../../shared/components/data-table/data-table.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AuditLogPage {
  private readonly api = inject(AuditApi);

  readonly modules = AUDIT_MODULES;
  readonly actions = AUDIT_ACTIONS;
  readonly when = formatWhen;
  readonly loading = signal(true);
  readonly exporting = signal(false);
  readonly failed = signal(false);
  readonly errorMessage = signal('');
  readonly rows = signal<AuditSummary[]>([]);
  readonly page = signal(0);
  readonly total = signal(0);
  readonly from = signal('');
  readonly to = signal('');
  readonly user = signal('');
  readonly module = signal('');
  readonly action = signal('');
  readonly entity = signal('');
  readonly search = signal('');
  readonly searchToken = signal(0);
  readonly selected = signal<AuditDetail | null>(null);
  readonly detailFailed = signal('');
  readonly pageSize = 20;

  constructor() {
    this.load();
  }

  onSearch(value: string): void {
    this.search.set(value);
    this.page.set(0);
    this.load();
  }

  apply(): void {
    this.page.set(0);
    this.load();
  }

  reset(): void {
    this.from.set('');
    this.to.set('');
    this.user.set('');
    this.module.set('');
    this.action.set('');
    this.entity.set('');
    this.search.set('');
    this.searchToken.update((value) => value + 1);
    this.page.set(0);
    this.selected.set(null);
    this.load();
  }

  onPage(page: number): void {
    this.page.set(page);
    this.load();
  }

  open(row: AuditSummary): void {
    this.detailFailed.set('');
    this.api.get(row.id).subscribe({
      next: (detail) => this.selected.set(detail),
      error: (error: unknown) => this.detailFailed.set(readApiError(error)),
    });
  }

  exportLog(format: 'csv' | 'xlsx' | 'pdf'): void {
    this.exporting.set(true);
    this.api.export(format, this.query()).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = `audit-log.${format}`;
        anchor.click();
        URL.revokeObjectURL(url);
        this.exporting.set(false);
      },
      error: (error: unknown) => {
        this.failed.set(true);
        this.errorMessage.set(readApiError(error));
        this.exporting.set(false);
      },
    });
  }

  private load(): void {
    this.loading.set(true);
    this.failed.set(false);
    this.api.search(this.query()).subscribe({
      next: (result) => {
        this.rows.set(result.items);
        this.total.set(result.totalElements);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.failed.set(true);
        this.errorMessage.set(readApiError(error));
        this.rows.set([]);
        this.loading.set(false);
      },
    });
  }

  private query(): AuditQuery {
    return {
      from: this.from(),
      to: this.to(),
      user: this.user(),
      module: this.module(),
      action: this.action(),
      entity: this.entity(),
      search: this.search(),
      page: this.page(),
    };
  }
}

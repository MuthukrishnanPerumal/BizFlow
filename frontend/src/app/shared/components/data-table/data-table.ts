import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { EmptyState } from '../empty-state/empty-state';
import { SkeletonLoader } from '../skeleton-loader/skeleton-loader';
import { StatusBadge } from '../status-badge/status-badge';

export interface TableColumn<T> {
  id: string;
  header: string;
  align?: 'start' | 'end';
  kind?: 'text' | 'status';
  value: (row: T) => string | null;
}

@Component({
  selector: 'app-data-table',
  imports: [EmptyState, SkeletonLoader, StatusBadge],
  templateUrl: './data-table.html',
  styleUrl: './data-table.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DataTable<T> {
  readonly columns = input.required<readonly TableColumn<T>[]>();
  readonly rows = input.required<readonly T[]>();
  readonly loading = input(false);
  readonly rowId = input.required<(row: T) => string>();
  readonly emptyTitle = input('Nothing to show');
  readonly emptyMessage = input('No records match the current view.');
}

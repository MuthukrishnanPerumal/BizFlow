import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

@Component({
  selector: 'app-skeleton-loader, app-loading-state',
  template: `
    <div
      class="bf-skeleton"
      [class.is-table]="layout() === 'table'"
      role="status"
      aria-live="polite"
      aria-busy="true"
      aria-label="Loading"
    >
      @for (row of lines(); track row) {
        <span aria-hidden="true"></span>
      }
    </div>
  `,
  styleUrl: './skeleton-loader.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SkeletonLoader {
  readonly rows = input(4);
  readonly layout = input<'lines' | 'table'>('lines');
  readonly lines = computed(() => Array.from({ length: this.rows() }, (_, index) => index));
}

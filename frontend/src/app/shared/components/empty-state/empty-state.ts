import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'app-empty-state',
  imports: [MatIconModule],
  template: `
    <div class="bf-empty">
      <mat-icon aria-hidden="true">inbox</mat-icon>
      <h2>{{ title() }}</h2>
      <p>{{ message() }}</p>
      <div class="bf-empty-action">
        <ng-content />
      </div>
    </div>
  `,
  styleUrl: './empty-state.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EmptyState {
  readonly title = input('Nothing to show');
  readonly message = input('There are no records to display.');
}

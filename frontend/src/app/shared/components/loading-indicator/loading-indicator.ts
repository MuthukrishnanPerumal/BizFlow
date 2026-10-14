import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { MatProgressBarModule } from '@angular/material/progress-bar';

@Component({
  selector: 'app-loading-indicator',
  imports: [MatProgressBarModule],
  template: `
    <div class="bf-loading" [class.active]="active()">
      @if (active()) {
        <mat-progress-bar mode="indeterminate" />
      }
    </div>
  `,
  styleUrl: './loading-indicator.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoadingIndicator {
  readonly active = input(false);
}

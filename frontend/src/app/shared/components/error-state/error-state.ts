import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'app-error-state',
  imports: [MatButtonModule, MatIconModule],
  template: `
    <div class="bf-error" role="alert">
      <mat-icon aria-hidden="true">error_outline</mat-icon>
      <h2>{{ title() }}</h2>
      <p>{{ message() }}</p>
      <button type="button" mat-stroked-button (click)="retry.emit()">{{ actionLabel() }}</button>
    </div>
  `,
  styleUrl: './error-state.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ErrorState {
  readonly title = input('Something went wrong');
  readonly message = input('This could not be loaded. Try again, or refresh the page.');
  readonly actionLabel = input('Try again');
  readonly retry = output<void>();
}

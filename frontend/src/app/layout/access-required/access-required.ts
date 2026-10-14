import { ChangeDetectionStrategy, Component } from '@angular/core';

import { ErrorState } from '../../shared/components/error-state/error-state';

@Component({
  selector: 'app-access-required',
  imports: [ErrorState],
  template: `
    <div class="bf-access">
      <app-error-state
        title="Authentication is required"
        message="This environment requires a signed-in session before the workspace can open."
        actionLabel="Reload"
        (retry)="reload()"
      />
    </div>
  `,
  styles: `
    .bf-access {
      min-height: 100vh;
      display: grid;
      place-items: center;
      padding: 24px;
      background: var(--bf-bg);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AccessRequired {
  reload(): void {
    window.location.assign('/');
  }
}

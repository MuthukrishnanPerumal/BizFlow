import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Router } from '@angular/router';

import { ErrorState } from '../../shared/components/error-state/error-state';

@Component({
  selector: 'app-not-found',
  imports: [ErrorState],
  template: `
    <app-error-state
      title="Page not found"
      message="The address does not match a workspace in this application."
      actionLabel="Back to dashboard"
      (retry)="goHome()"
    />
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NotFoundPage {
  private readonly router = inject(Router);

  goHome(): void {
    void this.router.navigateByUrl('/dashboard');
  }
}

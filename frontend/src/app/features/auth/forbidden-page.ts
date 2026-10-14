import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'app-forbidden-page',
  imports: [RouterLink, MatIconModule],
  template: `
    <section class="denied" role="alert">
      <mat-icon>lock</mat-icon>
      <h1>You do not have access</h1>
      <p>This area is limited to accounts with the required permission.</p>
      <a routerLink="/dashboard">Back to dashboard</a>
    </section>
  `,
  styles: `
    .denied {
      max-width: 520px;
      background: var(--bf-surface);
      border: 1px solid var(--bf-border);
      border-radius: var(--bf-radius);
      padding: 24px;
    }
    mat-icon {
      color: var(--bf-danger);
    }
    h1 {
      margin: 8px 0 0;
      font-size: 22px;
    }
    p {
      color: var(--bf-text-secondary);
    }
    a {
      color: var(--bf-accent);
      font-weight: 600;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ForbiddenPage {}

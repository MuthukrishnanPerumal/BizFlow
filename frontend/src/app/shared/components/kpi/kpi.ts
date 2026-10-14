import { ChangeDetectionStrategy, Component, input } from '@angular/core';

export type KpiTone = 'default' | 'success' | 'warning' | 'danger' | 'info';

@Component({
  selector: 'app-kpi',
  template: `
    <article class="bf-kpi">
      <span class="bf-kpi-label">{{ label() }}</span>
      <strong class="bf-kpi-value" [attr.data-tone]="tone()">{{ value() }}</strong>
      @if (hint()) {
        <small>{{ hint() }}</small>
      }
    </article>
  `,
  styles: `
    .bf-kpi {
      display: flex;
      flex-direction: column;
      gap: var(--bf-space-1);
      min-width: 0;
      padding: var(--bf-space-3) var(--bf-space-4);
      background: var(--bf-surface);
      border: 1px solid var(--bf-border);
      border-radius: var(--bf-radius);
    }

    .bf-kpi-label {
      color: var(--bf-text-secondary);
      font-size: var(--bf-text-label-size);
      font-weight: var(--bf-weight-medium);
      line-height: var(--bf-leading-tight);
    }

    .bf-kpi-value {
      color: var(--bf-text);
      font-size: var(--bf-text-kpi-size);
      font-weight: var(--bf-weight-semibold);
      line-height: var(--bf-leading-tight);
      letter-spacing: -0.02em;
      font-variant-numeric: tabular-nums lining-nums;
      font-feature-settings: 'tnum' 1;
    }

    .bf-kpi-value[data-tone='success'] {
      color: var(--bf-success);
    }

    .bf-kpi-value[data-tone='warning'] {
      color: var(--bf-warning);
    }

    .bf-kpi-value[data-tone='danger'] {
      color: var(--bf-danger);
    }

    .bf-kpi-value[data-tone='info'] {
      color: var(--bf-info);
    }

    small {
      color: var(--bf-text-muted);
      font-size: var(--bf-text-helper-size);
      line-height: var(--bf-leading-body);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Kpi {
  readonly label = input.required<string>();
  readonly value = input.required<string>();
  readonly hint = input('');
  readonly tone = input<KpiTone>('default');
}

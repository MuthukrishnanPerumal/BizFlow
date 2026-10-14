import { ChangeDetectionStrategy, Component, input } from '@angular/core';

@Component({
  selector: 'app-detail-section',
  template: `
    <section class="bf-detail-section">
      <header>
        <div>
          <h2>{{ title() }}</h2>
          @if (description()) {
            <p>{{ description() }}</p>
          }
        </div>
        <div class="bf-detail-section-actions">
          <ng-content select="[sectionActions]" />
        </div>
      </header>
      <div class="bf-detail-section-body">
        <ng-content />
      </div>
    </section>
  `,
  styles: `
    .bf-detail-section {
      border: 1px solid var(--bf-border);
      background: var(--bf-surface);
      border-radius: var(--bf-radius);
      padding: var(--bf-space-3) var(--bf-space-4) var(--bf-space-4);
    }

    header {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: var(--bf-space-3);
    }

    h2 {
      margin: 0;
      font-size: var(--bf-text-card-size);
      font-weight: var(--bf-weight-semibold);
      line-height: var(--bf-leading-tight);
      color: var(--bf-text);
    }

    header p {
      margin: var(--bf-space-1) 0 0;
      color: var(--bf-text-secondary);
      font-size: var(--bf-text-helper-size);
      line-height: var(--bf-leading-body);
    }

    .bf-detail-section-actions {
      display: flex;
      align-items: center;
      gap: var(--bf-space-2);
    }

    .bf-detail-section-actions:empty {
      display: none;
    }

    .bf-detail-section-body {
      margin-top: var(--bf-space-3);
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DetailSection {
  readonly title = input.required<string>();
  readonly description = input('');
}

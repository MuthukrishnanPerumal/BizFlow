import { ChangeDetectionStrategy, Component, input } from '@angular/core';

@Component({
  selector: 'app-form-section',
  template: `
    <section class="bf-form-section">
      <header>
        <h2>{{ title() }}</h2>
        @if (description()) {
          <p>{{ description() }}</p>
        }
      </header>
      <div class="bf-form-section-body">
        <ng-content />
      </div>
    </section>
  `,
  styleUrl: './form-section.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FormSection {
  readonly title = input.required<string>();
  readonly description = input('');
}

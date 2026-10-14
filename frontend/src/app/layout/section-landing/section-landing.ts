import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';

import { EmptyState } from '../../shared/components/empty-state/empty-state';
import { PageHeader } from '../../shared/components/page-header/page-header';

@Component({
  selector: 'app-section-landing',
  imports: [PageHeader, EmptyState],
  template: `
    <app-page-header [title]="title()" [subtitle]="summary()" />
    <app-empty-state title="No records" message="There are no records in this workspace." />
  `,
  styles: `
    :host {
      display: block;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SectionLanding {
  private readonly route = inject(ActivatedRoute);
  private readonly data = toSignal(this.route.data, { initialValue: this.route.snapshot.data });

  readonly title = computed(() => this.text('title', 'Workspace'));
  readonly summary = computed(() => this.text('summary', ''));

  private text(key: string, fallback: string): string {
    const value = this.data()[key];
    return typeof value === 'string' ? value : fallback;
  }
}

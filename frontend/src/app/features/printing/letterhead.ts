import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';

import { SettingsApi } from '../settings/settings-api.service';
import { Letterhead } from '../settings/settings.model';

@Component({
  selector: 'app-letterhead',
  template: `
    <div class="letterhead">
      <p class="company">{{ profile()?.companyName || 'Aveline BizFlow' }}</p>
      @if (profile()?.companyAddress) {
        <p>{{ profile()?.companyAddress }}</p>
      }
      @if (profile()?.phone || profile()?.email) {
        <p>{{ profile()?.phone }} {{ profile()?.email }}</p>
      }
      @if (profile()?.taxNumber) {
        <p>{{ profile()?.taxName || 'Tax' }} {{ profile()?.taxNumber }}</p>
      }
    </div>
  `,
  styles: `
    .letterhead p {
      margin: 0;
    }
    .company {
      letter-spacing: 0.04em;
      text-transform: uppercase;
      font-size: 12px;
      color: #5c6b7a;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LetterheadBlock {
  private readonly api = inject(SettingsApi);
  readonly profile = signal<Letterhead | null>(null);

  constructor() {
    this.api.letterhead().subscribe({
      next: (profile) => this.profile.set(profile),
      error: () => this.profile.set(null),
    });
  }
}

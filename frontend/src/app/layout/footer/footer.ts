import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { SystemSnapshot } from '../../core/services/system-snapshot.service';

@Component({
  selector: 'app-footer',
  template: `
    <footer class="bf-footer">
      <span>{{ snapshot.preferences()?.displayName ?? 'Aveline BizFlow' }}</span>
      <span>{{ snapshot.info()?.version ?? 'dev' }} · {{ snapshot.info()?.environment ?? 'starting' }}</span>
    </footer>
  `,
  styleUrl: './footer.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Footer {
  readonly snapshot = inject(SystemSnapshot);
}

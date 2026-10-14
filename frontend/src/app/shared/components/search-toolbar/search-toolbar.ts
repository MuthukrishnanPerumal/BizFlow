import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

import { SearchBar } from '../search-bar/search-bar';

@Component({
  selector: 'app-search-toolbar',
  imports: [SearchBar],
  template: `
    <div class="bf-search-toolbar" role="search">
      <app-search-bar [placeholder]="placeholder()" [token]="token()" (queryChange)="queryChange.emit($event)" />
      <div class="bf-search-toolbar-actions">
        <ng-content />
      </div>
    </div>
  `,
  styles: `
    .bf-search-toolbar {
      display: flex;
      align-items: flex-end;
      gap: var(--bf-space-3);
      min-width: 0;
    }

    app-search-bar {
      flex: 1 1 240px;
      min-width: 0;
    }

    .bf-search-toolbar-actions {
      display: flex;
      align-items: center;
      flex-wrap: wrap;
      gap: var(--bf-space-2);
    }

    .bf-search-toolbar-actions:empty {
      display: none;
    }

    @media (max-width: 720px) {
      .bf-search-toolbar {
        flex-direction: column;
        align-items: stretch;
      }
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SearchToolbar {
  readonly placeholder = input('Search');
  readonly token = input(0);
  readonly queryChange = output<string>();
}

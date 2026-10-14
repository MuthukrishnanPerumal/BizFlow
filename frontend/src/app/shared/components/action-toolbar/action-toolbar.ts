import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'app-action-toolbar',
  template: `
    <div class="bf-toolbar" role="toolbar" aria-label="Actions">
      <div class="bf-toolbar-start">
        <ng-content select="[toolbarStart]" />
      </div>
      <div class="bf-toolbar-end">
        <ng-content />
      </div>
    </div>
  `,
  styleUrl: './action-toolbar.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ActionToolbar {}

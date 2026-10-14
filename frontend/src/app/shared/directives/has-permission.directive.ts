import { Directive, TemplateRef, ViewContainerRef, effect, inject, input } from '@angular/core';

import { AuthService } from '../../core/auth/auth.service';

@Directive({ selector: '[appHasPermission]' })
export class HasPermissionDirective {
  private readonly template = inject(TemplateRef<unknown>);
  private readonly view = inject(ViewContainerRef);
  private readonly auth = inject(AuthService);
  readonly appHasPermission = input.required<string>();

  constructor() {
    effect(() => {
      this.view.clear();
      if (this.auth.hasPermission(this.appHasPermission())) {
        this.view.createEmbeddedView(this.template);
      }
    });
  }
}

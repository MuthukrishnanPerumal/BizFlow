import { CanDeactivateFn } from '@angular/router';
import { inject } from '@angular/core';

import { ConfirmationService } from '../services/confirmation.service';

export interface UnsavedForm {
  hasUnsavedChanges(): boolean;
}

export const unsavedChangesGuard: CanDeactivateFn<UnsavedForm> = (component) => {
  if (!component.hasUnsavedChanges()) {
    return true;
  }
  return inject(ConfirmationService).confirm({
    title: 'Discard changes',
    message: 'You have unsaved changes. Leave this page without saving?',
    confirmLabel: 'Leave page',
    cancelLabel: 'Stay',
    destructive: true,
  });
};

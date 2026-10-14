import { Injectable } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { Observable, map } from 'rxjs';

import { ConfirmationDialog, ConfirmationRequest } from '../../shared/dialogs/confirmation-dialog/confirmation-dialog';

@Injectable({ providedIn: 'root' })
export class ConfirmationService {
  constructor(private readonly dialog: MatDialog) {}

  confirm(request: ConfirmationRequest): Observable<boolean> {
    return this.dialog
      .open(ConfirmationDialog, {
        data: request,
        width: '440px',
        maxWidth: 'calc(100vw - 32px)',
        autoFocus: 'first-tabbable',
        restoreFocus: true,
        role: request.destructive ? 'alertdialog' : 'dialog',
        ariaDescribedBy: 'bf-confirm-detail',
        panelClass: request.destructive ? 'bf-dialog-danger' : 'bf-dialog',
      })
      .afterClosed()
      .pipe(map((result) => result === true));
  }
}

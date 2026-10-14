import { Injectable, inject } from '@angular/core';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';

import { CommandPalette } from './command-palette';

@Injectable({ providedIn: 'root' })
export class CommandPaletteService {
  private readonly dialog = inject(MatDialog);
  private ref: MatDialogRef<CommandPalette> | null = null;

  get isOpen(): boolean {
    return this.ref !== null;
  }

  toggle(): void {
    if (this.ref) {
      this.ref.close();
      return;
    }
    this.ref = this.dialog.open(CommandPalette, {
      width: '560px',
      maxWidth: 'calc(100vw - 32px)',
      autoFocus: 'first-tabbable',
      restoreFocus: true,
      panelClass: 'bf-command-dialog',
      position: { top: '12vh' },
    });
    this.ref.afterClosed().subscribe(() => {
      this.ref = null;
    });
  }
}

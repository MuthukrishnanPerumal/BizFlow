import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

export interface ConfirmationRequest {
  title: string;
  message: string;
  confirmLabel: string;
  cancelLabel: string;
  destructive: boolean;
}

@Component({
  selector: 'app-confirmation-dialog',
  imports: [MatDialogModule, MatButtonModule, MatIconModule],
  templateUrl: './confirmation-dialog.html',
  styleUrl: './confirmation-dialog.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ConfirmationDialog {
  readonly data = inject<ConfirmationRequest>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<ConfirmationDialog, boolean>);

  cancel(): void {
    this.dialogRef.close(false);
  }

  confirm(): void {
    this.dialogRef.close(true);
  }
}

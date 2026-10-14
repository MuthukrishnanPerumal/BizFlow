import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';

import { ConfirmationService } from '../../core/services/confirmation.service';
import { readApiError } from '../../core/utils/http-error.util';
import { SettingsApi } from './settings-api.service';
import { ResetTableOption } from './settings.model';

@Component({
  selector: 'app-data-reset-dialog',
  imports: [MatDialogModule, MatButtonModule, MatCheckboxModule],
  templateUrl: './data-reset-dialog.html',
  styleUrl: './data-reset-dialog.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DataResetDialog {
  private readonly api = inject(SettingsApi);
  private readonly confirmation = inject(ConfirmationService);
  private readonly dialogRef = inject(MatDialogRef<DataResetDialog, number>);

  readonly loading = signal(true);
  readonly deleting = signal(false);
  readonly errorMessage = signal('');
  readonly tables = signal<ResetTableOption[]>([]);
  readonly selected = signal<ReadonlySet<string>>(new Set());
  readonly groups = computed(() => {
    const grouped = new Map<string, ResetTableOption[]>();
    for (const table of this.tables()) {
      const rows = grouped.get(table.group) ?? [];
      rows.push(table);
      grouped.set(table.group, rows);
    }
    return [...grouped.entries()].map(([name, rows]) => ({ name, rows }));
  });
  readonly allSelected = computed(() => this.tables().length > 0 && this.selected().size === this.tables().length);
  readonly someSelected = computed(() => this.selected().size > 0 && !this.allSelected());

  constructor() {
    this.api.resetTables().subscribe({
      next: (tables) => {
        this.tables.set(tables);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.errorMessage.set(readApiError(error));
        this.loading.set(false);
      },
    });
  }

  toggleAll(checked: boolean): void {
    this.selected.set(checked ? new Set(this.tables().map((table) => table.key)) : new Set());
  }

  toggle(key: string, checked: boolean): void {
    const next = new Set(this.selected());
    for (const item of checked ? this.withDescendants(key) : this.withAncestors(key)) {
      if (checked) {
        next.add(item);
      } else {
        next.delete(item);
      }
    }
    this.selected.set(next);
  }

  isSelected(key: string): boolean {
    return this.selected().has(key);
  }

  cancel(): void {
    this.dialogRef.close();
  }

  remove(): void {
    const keys = [...this.selected()];
    if (keys.length === 0 || this.deleting()) {
      return;
    }
    const noun = keys.length === 1 ? 'table' : 'tables';
    this.confirmation
      .confirm({
        title: 'Delete selected data',
        message: `Permanently delete every row in ${keys.length} selected ${noun}? This cannot be undone.`,
        confirmLabel: 'Delete',
        cancelLabel: 'Cancel',
        destructive: true,
      })
      .subscribe((confirmed) => {
        if (!confirmed) {
          return;
        }
        this.deleting.set(true);
        this.errorMessage.set('');
        this.api.resetData(keys).subscribe({
          next: (result) => this.dialogRef.close(result.deletedRows),
          error: (error: unknown) => {
            this.deleting.set(false);
            this.errorMessage.set(readApiError(error));
          },
        });
      });
  }

  private withDescendants(key: string): string[] {
    const requires = new Map(this.tables().map((table) => [table.key, table.requires]));
    const result: string[] = [];
    const seen = new Set<string>();
    const stack = [key];
    while (stack.length > 0) {
      const current = stack.pop();
      if (!current || seen.has(current)) {
        continue;
      }
      seen.add(current);
      result.push(current);
      for (const child of requires.get(current) ?? []) {
        stack.push(child);
      }
    }
    return result;
  }

  private withAncestors(key: string): string[] {
    const requires = new Map(this.tables().map((table) => [table.key, table.requires]));
    const result = new Set<string>([key]);
    let grew = true;
    while (grew) {
      grew = false;
      for (const [parent, children] of requires) {
        if (result.has(parent)) {
          continue;
        }
        if (children.some((child) => result.has(child))) {
          result.add(parent);
          grew = true;
        }
      }
    }
    return [...result];
  }
}

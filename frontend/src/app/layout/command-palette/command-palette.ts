import { ChangeDetectionStrategy, Component, HostListener, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { Router } from '@angular/router';
import { debounceTime, distinctUntilChanged, switchMap, tap } from 'rxjs';

import { CommandHit, CommandResults, CommandSearch } from './command-search';

@Component({
  selector: 'app-command-palette',
  imports: [ReactiveFormsModule, MatDialogModule, MatIconModule],
  templateUrl: './command-palette.html',
  styleUrl: './command-palette.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandPalette {
  readonly query = new FormControl('', { nonNullable: true });
  readonly results = signal<CommandResults>({ hits: [], pending: false, failed: false });
  readonly activeIndex = signal(0);
  readonly groups = computed(() => groupHits(this.results().hits));

  private readonly search = inject(CommandSearch);
  private readonly router = inject(Router);
  private readonly dialogRef = inject(MatDialogRef<CommandPalette>);

  constructor() {
    this.query.valueChanges
      .pipe(
        debounceTime(200),
        distinctUntilChanged(),
        tap((value) => {
          if (value.trim().length >= 2) {
            this.results.update((current) => ({ ...current, pending: true, failed: false }));
          }
        }),
        switchMap((value) => this.search.search(value)),
        takeUntilDestroyed(),
      )
      .subscribe((found) => {
        this.results.set(found);
        this.activeIndex.set(0);
      });
    this.search.search('').pipe(takeUntilDestroyed()).subscribe((found) => this.results.set(found));
  }

  @HostListener('keydown', ['$event'])
  onKey(event: KeyboardEvent): void {
    const hits = this.results().hits;
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      this.activeIndex.update((index) => (hits.length === 0 ? 0 : (index + 1) % hits.length));
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      this.activeIndex.update((index) => (hits.length === 0 ? 0 : (index - 1 + hits.length) % hits.length));
    } else if (event.key === 'Enter') {
      const hit = hits[this.activeIndex()];
      if (hit) {
        event.preventDefault();
        this.go(hit);
      }
    }
  }

  indexOf(hit: CommandHit): number {
    return this.results().hits.indexOf(hit);
  }

  go(hit: CommandHit): void {
    this.dialogRef.close();
    void this.router.navigateByUrl(hit.route);
  }
}

function groupHits(hits: readonly CommandHit[]): { label: string; hits: CommandHit[] }[] {
  const order = ['Page', 'Action', 'Product', 'Customer', 'Supplier', 'Invoice'] as const;
  return order
    .map((kind) => ({ label: kind === 'Page' ? 'Pages' : kind === 'Action' ? 'Actions' : `${kind}s`, hits: hits.filter((hit) => hit.kind === kind) }))
    .filter((group) => group.hits.length > 0);
}

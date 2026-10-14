import { ChangeDetectionStrategy, Component, DestroyRef, effect, inject, input, OnInit, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatOptionSelectionChange } from '@angular/material/core';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { debounceTime, distinctUntilChanged } from 'rxjs';

import { CatalogRecord, MasterRef } from '../../core/models/master.model';
import { CatalogApi } from './catalog-api.service';
import { CatalogResource } from './catalog.config';

@Component({
  selector: 'app-record-lookup',
  imports: [ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatAutocompleteModule],
  templateUrl: './record-lookup.html',
  styleUrl: './master-form.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RecordLookup implements OnInit {
  private readonly api = inject(CatalogApi);

  readonly label = input.required<string>();
  readonly resource = input.required<CatalogResource>();
  readonly selected = input<MasterRef | null>(null);
  readonly selectedChange = output<MasterRef | null>();

  readonly query = new FormControl('', { nonNullable: true });
  readonly options = signal<MasterRef[]>([]);
  private readonly shownId = signal<number | null>(null);

  constructor() {
    effect(() => {
      const current = this.selected();
      const id = current?.id ?? null;
      if (id !== this.shownId()) {
        this.shownId.set(id);
        this.query.setValue(current ? labelOf(current) : '', { emitEvent: false });
      }
    });
    this.query.valueChanges
      .pipe(debounceTime(200), distinctUntilChanged(), takeUntilDestroyed(inject(DestroyRef)))
      .subscribe((term) => {
        const current = this.selected();
        if (current && term.trim() === labelOf(current)) {
          return;
        }
        this.search(term);
      });
  }

  ngOnInit(): void {
    this.search('');
  }

  choose(event: MatOptionSelectionChange, option: MasterRef): void {
    if (!event.isUserInput) {
      return;
    }
    this.shownId.set(option.id);
    this.selectedChange.emit(option);
  }

  blur(): void {
    const current = this.selected();
    if (!this.query.value.trim()) {
      if (current) {
        this.shownId.set(null);
        this.selectedChange.emit(null);
      }
      return;
    }
    if (current) {
      this.query.setValue(labelOf(current), { emitEvent: false });
    }
  }

  private search(term: string): void {
    this.api.list(this.resource(), term.trim(), 'ACTIVE', 0, 'name,asc', 20).subscribe({
      next: (page) => this.options.set(page.items.map((item) => toRef(this.resource(), item))),
      error: () => this.options.set([]),
    });
  }
}

function labelOf(ref: MasterRef): string {
  return `${ref.name} (${ref.code})`;
}

function toRef(resource: CatalogResource, row: CatalogRecord): MasterRef {
  if (resource === 'units') {
    return { id: row.id, code: row.shortName ?? '', name: row.name, decimalAllowed: row.decimalAllowed };
  }
  return { id: row.id, code: row.code ?? '', name: row.name };
}

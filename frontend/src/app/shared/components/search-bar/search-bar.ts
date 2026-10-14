import { ChangeDetectionStrategy, Component, DestroyRef, effect, inject, input, output } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { debounceTime, distinctUntilChanged } from 'rxjs';

@Component({
  selector: 'app-search-bar',
  imports: [ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatIconModule],
  templateUrl: './search-bar.html',
  styleUrl: './search-bar.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SearchBar {
  readonly placeholder = input('Search');
  readonly token = input(0);
  readonly queryChange = output<string>();

  readonly control = new FormControl('', { nonNullable: true });

  clear(): void {
    this.control.setValue('');
  }

  constructor() {
    effect(() => {
      if (this.token() > 0) {
        this.control.setValue('', { emitEvent: false });
      }
    });
    this.control.valueChanges
      .pipe(debounceTime(200), distinctUntilChanged(), takeUntilDestroyed(inject(DestroyRef)))
      .subscribe((value) => this.queryChange.emit(value.trim()));
  }
}

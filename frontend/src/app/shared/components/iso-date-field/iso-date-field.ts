import { ChangeDetectionStrategy, Component, computed, effect, forwardRef, input, output, signal } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

import { dateFormatVersion, dateInputHint, parseUserDate, toIsoDate } from '../../../features/master/master-format';

@Component({
  selector: 'app-iso-date-field',
  imports: [MatFormFieldModule, MatInputModule, MatDatepickerModule],
  template: `
    <mat-form-field appearance="outline" floatLabel="always" [subscriptSizing]="subscriptSizing()" class="bf-date">
      <mat-label>{{ label() }}</mat-label>
      <input
        matInput
        [matDatepicker]="picker"
        [value]="picked()"
        [placeholder]="hint()"
        [disabled]="disabled()"
        autocomplete="off"
        (dateChange)="commit($event.value)"
        (blur)="onTouched()"
      />
      <mat-datepicker-toggle matIconSuffix [for]="picker" />
      <mat-datepicker #picker />
    </mat-form-field>
  `,
  styles: `
    :host {
      display: block;
      width: 100%;
      min-width: 0;
    }
    .bf-date {
      width: 100%;
    }
  `,
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => IsoDateField),
      multi: true,
    },
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class IsoDateField implements ControlValueAccessor {
  readonly label = input('Date');
  readonly value = input<string | undefined>(undefined);
  readonly subscriptSizing = input<'fixed' | 'dynamic'>('fixed');
  readonly committed = output<string>();

  readonly iso = signal('');
  readonly disabled = signal(false);
  readonly hint = computed(() => {
    dateFormatVersion();
    return dateInputHint();
  });
  readonly picked = computed(() => {
    dateFormatVersion();
    const iso = this.iso();
    return iso ? parseUserDate(iso) : null;
  });

  private onChange: (value: string) => void = () => undefined;
  onTouched: () => void = () => undefined;

  constructor() {
    effect(() => {
      const incoming = this.value();
      if (incoming !== undefined) {
        this.iso.set(incoming ?? '');
      }
    });
  }

  writeValue(value: string | null): void {
    this.iso.set(value ?? '');
  }

  registerOnChange(fn: (value: string) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled.set(isDisabled);
  }

  commit(date: Date | null): void {
    const next = date ? toIsoDate(date) : '';
    this.iso.set(next);
    this.onChange(next);
    this.onTouched();
    this.committed.emit(next);
  }
}

import { ChangeDetectionStrategy, Component, input, model } from '@angular/core';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

@Component({
  selector: 'app-date-field',
  imports: [MatFormFieldModule, MatInputModule, MatDatepickerModule],
  template: `
    <mat-form-field appearance="outline" subscriptSizing="dynamic" class="bf-date">
      <mat-label>{{ label() }}</mat-label>
      <input
        matInput
        [matDatepicker]="picker"
        [value]="value()"
        (dateChange)="value.set($event.value)"
      />
      <mat-datepicker-toggle matIconSuffix [for]="picker" />
      <mat-datepicker #picker />
    </mat-form-field>
  `,
  styles: `
    :host {
      display: block;
    }
    .bf-date {
      width: 100%;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DateField {
  readonly label = input('Date');
  readonly value = model<Date | null>(null);
}

import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';

import { IsoDateField } from '../../shared/components/iso-date-field/iso-date-field';
import { PRESET_OPTIONS, ReportPreset } from './reporting.model';

@Component({
  selector: 'app-period-filter',
  imports: [MatButtonModule, MatFormFieldModule, MatSelectModule, IsoDateField],
  templateUrl: './period-filter.html',
  styleUrl: './period-filter.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PeriodFilter {
  readonly preset = input.required<ReportPreset>();
  readonly from = input('');
  readonly to = input('');
  readonly presetChange = output<ReportPreset>();
  readonly fromChange = output<string>();
  readonly toChange = output<string>();
  readonly reset = output<void>();
  readonly options = PRESET_OPTIONS;

  choose(preset: ReportPreset): void {
    this.presetChange.emit(preset);
  }
}

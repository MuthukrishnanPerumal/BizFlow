import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

export interface FilterChip {
  id: string;
  label: string;
}

@Component({
  selector: 'app-smart-filters',
  imports: [MatIconModule],
  templateUrl: './smart-filters.html',
  styleUrl: './smart-filters.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SmartFilters {
  readonly chips = input<readonly FilterChip[]>([]);
  readonly advanced = input(false);
  readonly forceOpen = input(false);
  readonly remove = output<string>();
  readonly clear = output<void>();

  private readonly manual = signal<boolean | null>(null);
  readonly open = computed(() => this.manual() ?? this.forceOpen());

  toggle(): void {
    this.manual.set(!this.open());
  }
}

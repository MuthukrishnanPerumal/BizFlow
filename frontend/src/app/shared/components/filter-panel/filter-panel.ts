import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

let nextFilterId = 0;

@Component({
  selector: 'app-filter-panel, app-filter-bar',
  imports: [MatIconModule],
  templateUrl: './filter-panel.html',
  styleUrl: './filter-panel.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FilterPanel {
  readonly title = input('Filters');
  readonly initiallyOpen = input(true);
  private readonly expandedState = signal<boolean | null>(null);
  readonly expanded = computed(() => this.expandedState() ?? this.initiallyOpen());
  readonly panelId = `bf-filters-${++nextFilterId}`;

  toggle(): void {
    this.expandedState.set(!this.expanded());
  }
}

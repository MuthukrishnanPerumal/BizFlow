import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'app-pagination',
  imports: [MatButtonModule, MatIconModule],
  templateUrl: './pagination.html',
  styleUrl: './pagination.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Pagination {
  readonly page = input.required<number>();
  readonly pageSize = input.required<number>();
  readonly total = input.required<number>();
  readonly pageChange = output<number>();

  readonly totalPages = computed(() => Math.max(1, Math.ceil(this.total() / this.pageSize())));
  readonly rangeStart = computed(() => (this.total() === 0 ? 0 : this.page() * this.pageSize() + 1));
  readonly rangeEnd = computed(() => Math.min(this.total(), (this.page() + 1) * this.pageSize()));

  previous(): void {
    if (this.page() > 0) {
      this.pageChange.emit(this.page() - 1);
    }
  }

  next(): void {
    if (this.page() + 1 < this.totalPages()) {
      this.pageChange.emit(this.page() + 1);
    }
  }
}

import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

import { PermissionGroup } from '../../core/models/identity.model';
import { EmptyState } from '../../shared/components/empty-state/empty-state';
import { PageHeader } from '../../shared/components/page-header/page-header';
import { SkeletonLoader } from '../../shared/components/skeleton-loader/skeleton-loader';
import { RoleApi } from '../roles/role-api.service';

@Component({
  selector: 'app-permission-list-page',
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, PageHeader, EmptyState, SkeletonLoader],
  templateUrl: './permission-list-page.html',
  styleUrl: './permission-list-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PermissionListPage {
  private readonly api = inject(RoleApi);

  readonly loading = signal(true);
  readonly groups = signal<PermissionGroup[]>([]);
  readonly query = new FormControl('', { nonNullable: true });
  private readonly term = signal('');
  readonly visible = computed(() => {
    const needle = this.term().trim().toLowerCase();
    if (!needle) {
      return this.groups();
    }
    return this.groups()
      .map((group) => ({
        ...group,
        permissions: group.permissions.filter((permission) =>
          `${group.label} ${permission.name} ${permission.code}`.toLowerCase().includes(needle),
        ),
      }))
      .filter((group) => group.permissions.length > 0);
  });

  constructor() {
    this.query.valueChanges.pipe(takeUntilDestroyed()).subscribe((value) => this.term.set(value));
    this.api.permissions().subscribe({
      next: (groups) => {
        this.groups.set(groups);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
      },
    });
  }
}

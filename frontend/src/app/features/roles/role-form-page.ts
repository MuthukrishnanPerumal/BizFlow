import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { forkJoin, of } from 'rxjs';

import { PermissionGroup } from '../../core/models/identity.model';
import { AuthService } from '../../core/auth/auth.service';
import { ConfirmationService } from '../../core/services/confirmation.service';
import { NotificationService } from '../../core/services/notification.service';
import { readApiError, readFieldErrors } from '../../core/utils/http-error.util';
import { FormSection } from '../../shared/components/form-section/form-section';
import { PageHeader } from '../../shared/components/page-header/page-header';
import { SkeletonLoader } from '../../shared/components/skeleton-loader/skeleton-loader';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { RoleApi } from './role-api.service';

@Component({
  selector: 'app-role-form-page',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    RouterLink,
    PageHeader,
    SkeletonLoader,
    FormSection,
    HasPermissionDirective,
  ],
  templateUrl: './role-form-page.html',
  styleUrl: './role-form-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RoleFormPage {
  private readonly formBuilder = inject(FormBuilder);
  private readonly api = inject(RoleApi);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly notifications = inject(NotificationService);
  private readonly confirmation = inject(ConfirmationService);
  readonly auth = inject(AuthService);

  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly groups = signal<PermissionGroup[]>([]);
  readonly selected = signal<ReadonlySet<number>>(new Set());
  readonly roleId = signal<number | null>(null);
  readonly version = signal(0);
  readonly systemRole = signal(false);

  readonly form = this.formBuilder.nonNullable.group({
    code: ['', [Validators.required, Validators.pattern(/^[A-Z][A-Z0-9_]{1,63}$/)]],
    name: ['', [Validators.required, Validators.maxLength(128)]],
    description: ['', Validators.maxLength(500)],
  });

  constructor() {
    const idParam = this.route.snapshot.paramMap.get('id');
    const id = idParam ? Number(idParam) : null;
    if (idParam && !Number.isInteger(id)) {
      void this.router.navigate(['/roles']);
      return;
    }
    forkJoin({
      groups: this.api.permissions(),
      role: id == null ? of(null) : this.api.get(id),
    }).subscribe({
      next: ({ groups, role }) => {
        this.groups.set(groups);
        if (role) {
          this.roleId.set(role.id);
          this.version.set(role.version);
          this.systemRole.set(role.systemRole);
          this.selected.set(new Set(role.permissions.map((permission) => permission.id)));
          this.form.patchValue({
            code: role.code,
            name: role.name,
            description: role.description ?? '',
          });
          this.form.controls.code.disable();
        }
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.loading.set(false);
        this.notifications.error(readApiError(error));
        void this.router.navigate(['/roles']);
      },
    });
  }

  get editing(): boolean {
    return this.roleId() !== null;
  }

  invalid(name: 'code' | 'name' | 'description'): boolean {
    const control = this.form.controls[name];
    return control.invalid && (control.touched || this.form.touched);
  }

  toggle(id: number, checked: boolean): void {
    this.selected.update((current) => {
      const next = new Set(current);
      if (checked) {
        next.add(id);
      } else {
        next.delete(id);
      }
      return next;
    });
  }

  moduleSelected(group: PermissionGroup): boolean {
    return group.permissions.every((permission) => this.selected().has(permission.id));
  }

  toggleModule(group: PermissionGroup, checked: boolean): void {
    this.selected.update((current) => {
      const next = new Set(current);
      for (const permission of group.permissions) {
        if (checked) {
          next.add(permission.id);
        } else {
          next.delete(permission.id);
        }
      }
      return next;
    });
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    const value = this.form.getRawValue();
    const description = value.description.trim();
    const permissionIds = [...this.selected()];
    const request = this.editing
      ? this.api.update(this.roleId()!, {
          name: value.name.trim(),
          description: description.length > 0 ? description : null,
          permissionIds,
          version: this.version(),
        })
      : this.api.create({
          code: value.code.trim(),
          name: value.name.trim(),
          description: description.length > 0 ? description : null,
          permissionIds,
        });
    request.subscribe({
      next: () => {
        this.saving.set(false);
        this.notifications.success(this.editing ? 'Role updated' : 'Role created');
        void this.router.navigate(['/roles']);
      },
      error: (error: unknown) => {
        this.saving.set(false);
        for (const detail of readFieldErrors(error)) {
          if (detail.field) {
            this.form.get(detail.field)?.setErrors({ server: detail.message });
          }
        }
        this.notifications.error(readApiError(error));
      },
    });
  }

  remove(): void {
    const id = this.roleId();
    if (id == null || this.systemRole()) {
      return;
    }
    this.confirmation
      .confirm({
        title: 'Delete role',
        message: 'Delete this role? It must not be assigned to any user.',
        confirmLabel: 'Delete role',
        cancelLabel: 'Keep role',
        destructive: true,
      })
      .subscribe((confirmed) => {
        if (!confirmed) {
          return;
        }
        this.api.delete(id).subscribe({
          next: () => {
            this.notifications.success('Role deleted');
            void this.router.navigate(['/roles']);
          },
          error: (error: unknown) => this.notifications.error(readApiError(error)),
        });
      });
  }
}

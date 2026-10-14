import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { RoleSummary, UserRecord } from '../../core/models/identity.model';
import { ConfirmationService } from '../../core/services/confirmation.service';
import { NotificationService } from '../../core/services/notification.service';
import { readApiError, readFieldErrors } from '../../core/utils/http-error.util';
import { FormSection } from '../../shared/components/form-section/form-section';
import { PageHeader } from '../../shared/components/page-header/page-header';
import { SkeletonLoader } from '../../shared/components/skeleton-loader/skeleton-loader';
import { HasPermissionDirective } from '../../shared/directives/has-permission.directive';
import { RoleApi } from '../roles/role-api.service';
import { UserApi } from './user-api.service';

const PASSWORD_PATTERN = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9\s]).{10,72}$/;

@Component({
  selector: 'app-user-form-page',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    RouterLink,
    PageHeader,
    SkeletonLoader,
    FormSection,
    HasPermissionDirective,
  ],
  templateUrl: './user-form-page.html',
  styleUrl: './user-form-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UserFormPage {
  private readonly formBuilder = inject(FormBuilder);
  private readonly api = inject(UserApi);
  private readonly rolesApi = inject(RoleApi);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly notifications = inject(NotificationService);
  private readonly confirmation = inject(ConfirmationService);
  readonly auth = inject(AuthService);

  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly roles = signal<RoleSummary[]>([]);
  readonly selectedRoles = signal<ReadonlySet<number>>(new Set());
  readonly passwordVisible = signal(false);
  readonly userId = signal<number | null>(null);
  readonly version = signal<number | null>(null);
  readonly username = signal('');

  readonly form = this.formBuilder.nonNullable.group(
    {
      fullName: ['', [Validators.required, Validators.maxLength(160)]],
      email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
      phone: ['', Validators.pattern(/^$|^[0-9+()\-\s]{7,32}$/)],
      username: ['', [Validators.required, Validators.pattern(/^[A-Za-z0-9._-]{3,64}$/)]],
      password: [''],
      confirmPassword: [''],
      status: this.formBuilder.nonNullable.control<'ACTIVE' | 'INACTIVE' | 'LOCKED'>('ACTIVE', Validators.required),
    },
    { validators: passwordConfirmation },
  );

  constructor() {
    const idParam = this.route.snapshot.paramMap.get('id');
    const id = idParam ? Number(idParam) : null;
    if (idParam && !Number.isInteger(id)) {
      void this.router.navigate(['/users']);
      return;
    }
    const roles$ = this.rolesApi.list('', 0, 100);
    if (id == null) {
      this.form.controls.password.addValidators([Validators.required, Validators.pattern(PASSWORD_PATTERN)]);
      roles$.subscribe({
        next: (page) => {
          this.roles.set(page.items.map((role) => ({ id: role.id, code: role.code, name: role.name })));
          this.loading.set(false);
        },
        error: (error: unknown) => {
          this.loading.set(false);
          this.notifications.error(readApiError(error));
        },
      });
      return;
    }
    this.userId.set(id);
    this.form.controls.username.disable();
    this.form.controls.password.addValidators(Validators.pattern(new RegExp(`^$|${PASSWORD_PATTERN.source}`)));
    forkJoin({ roles: roles$, user: this.api.get(id) }).subscribe({
      next: ({ roles, user }) => {
        this.roles.set(roles.items.map((role) => ({ id: role.id, code: role.code, name: role.name })));
        this.fill(user);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.loading.set(false);
        this.notifications.error(readApiError(error));
        void this.router.navigate(['/users']);
      },
    });
  }

  get editing(): boolean {
    return this.userId() !== null;
  }

  invalid(name: 'fullName' | 'email' | 'phone' | 'username' | 'password' | 'confirmPassword' | 'status'): boolean {
    const control = this.form.controls[name];
    return control.invalid && (control.touched || this.form.touched);
  }

  toggleRole(id: number, checked: boolean): void {
    this.selectedRoles.update((current) => {
      const next = new Set(current);
      if (checked) {
        next.add(id);
      } else {
        next.delete(id);
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
    const phone = value.phone.trim();
    const body = {
      fullName: value.fullName.trim(),
      email: value.email.trim(),
      phone: phone.length > 0 ? phone : null,
      status: value.status,
      roleIds: [...this.selectedRoles()],
      password: value.password,
      username: value.username.trim(),
    };
    const request = this.editing
      ? this.api.update(this.userId()!, {
          fullName: body.fullName,
          email: body.email,
          phone: body.phone,
          status: body.status,
          roleIds: body.roleIds,
          version: this.version() ?? 0,
          ...(body.password ? { password: body.password } : {}),
        })
      : this.api.create(body);
    request.subscribe({
      next: () => {
        this.saving.set(false);
        this.notifications.success(this.editing ? 'User updated' : 'User created');
        const resetOwnPassword = this.editing && this.auth.user()?.id === this.userId() && body.password.length > 0;
        if (resetOwnPassword) {
          this.auth.logout().subscribe(() => void this.router.navigate(['/login']));
          return;
        }
        void this.router.navigate(['/users']);
      },
      error: (error: unknown) => {
        this.saving.set(false);
        this.applyServerErrors(error);
        this.notifications.error(readApiError(error));
      },
    });
  }

  remove(): void {
    const id = this.userId();
    if (id == null) {
      return;
    }
    this.confirmation
      .confirm({
        title: 'Delete user',
        message: 'Delete this account? They will no longer be able to sign in.',
        confirmLabel: 'Delete user',
        cancelLabel: 'Keep user',
        destructive: true,
      })
      .subscribe((confirmed) => {
        if (!confirmed) {
          return;
        }
        this.api.delete(id).subscribe({
          next: () => {
            this.notifications.success('User deleted');
            void this.router.navigate(['/users']);
          },
          error: (error: unknown) => this.notifications.error(readApiError(error)),
        });
      });
  }

  private fill(user: UserRecord): void {
    this.version.set(user.version);
    this.username.set(user.username);
    this.selectedRoles.set(new Set(user.roles.map((role) => role.id)));
    this.form.patchValue({
      fullName: user.fullName,
      email: user.email,
      phone: user.phone ?? '',
      username: user.username,
      status: user.status,
    });
  }

  private applyServerErrors(error: unknown): void {
    for (const detail of readFieldErrors(error)) {
      if (!detail.field) {
        continue;
      }
      this.form.get(detail.field)?.setErrors({ server: detail.message });
    }
  }
}

function passwordConfirmation(control: AbstractControl): ValidationErrors | null {
  const password = control.get('password')?.value;
  const confirm = control.get('confirmPassword')?.value;
  if (!password && !confirm) {
    return null;
  }
  return password === confirm ? null : { mismatch: true };
}

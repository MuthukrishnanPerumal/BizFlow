import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Router } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';
import { NotificationService } from '../../core/services/notification.service';
import { readApiError } from '../../core/utils/http-error.util';
import { FormSection } from '../../shared/components/form-section/form-section';
import { PageHeader } from '../../shared/components/page-header/page-header';

@Component({
  selector: 'app-change-password-page',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    PageHeader,
    FormSection,
  ],
  templateUrl: './change-password-page.html',
  styleUrl: './change-password-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ChangePasswordPage {
  private readonly formBuilder = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly notifications = inject(NotificationService);
  private readonly router = inject(Router);

  readonly submitting = signal(false);
  readonly error = signal('');
  readonly visible = signal(false);
  readonly forced = this.auth.user()?.mustChangePassword === true;
  readonly form = this.formBuilder.nonNullable.group(
    {
      currentPassword: ['', Validators.required],
      newPassword: ['', [Validators.required, Validators.minLength(10), Validators.maxLength(72), Validators.pattern(/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9\s]).{10,72}$/)]],
      confirmPassword: ['', Validators.required],
    },
    { validators: passwordsMatch },
  );

  invalid(name: 'currentPassword' | 'newPassword' | 'confirmPassword'): boolean {
    const control = this.form.controls[name];
    return control.invalid && (control.touched || this.form.touched);
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.error.set('');
    const value = this.form.getRawValue();
    this.auth.changePassword(value.currentPassword, value.newPassword).subscribe({
      next: () => {
        this.submitting.set(false);
        this.notifications.success('Password changed');
        void this.router.navigate(['/dashboard']);
      },
      error: (error: unknown) => {
        this.submitting.set(false);
        this.error.set(readApiError(error));
      },
    });
  }
}

function passwordsMatch(control: AbstractControl): ValidationErrors | null {
  const next = control.get('newPassword')?.value;
  const confirm = control.get('confirmPassword')?.value;
  return next && confirm && next !== confirm ? { mismatch: true } : null;
}

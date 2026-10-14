import { ChangeDetectionStrategy, Component, HostListener, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';
import { UnsavedForm } from '../../core/guards/unsaved-changes.guard';
import { RecordStatus } from '../../core/models/master.model';
import { ConfirmationService } from '../../core/services/confirmation.service';
import { NotificationService } from '../../core/services/notification.service';
import { readApiError, readFieldErrors } from '../../core/utils/http-error.util';
import { FormSection } from '../../shared/components/form-section/form-section';
import { PageHeader } from '../../shared/components/page-header/page-header';
import { SkeletonLoader } from '../../shared/components/skeleton-loader/skeleton-loader';
import { CatalogApi, CatalogWrite } from './catalog-api.service';
import { CatalogConfig, catalogConfig } from './catalog.config';
import { blankToNull } from './master-format';

@Component({
  selector: 'app-catalog-form-page',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    RouterLink,
    PageHeader,
    FormSection,
    SkeletonLoader,
  ],
  templateUrl: './catalog-form-page.html',
  styleUrl: './master-form.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CatalogFormPage implements UnsavedForm {
  private readonly formBuilder = inject(FormBuilder);
  private readonly api = inject(CatalogApi);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly notifications = inject(NotificationService);
  private readonly confirmation = inject(ConfirmationService);
  readonly auth = inject(AuthService);

  readonly config: CatalogConfig = catalogConfig(this.route.snapshot.data['resource']);
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly recordId = signal<number | null>(null);
  readonly version = signal<number | null>(null);

  readonly form = this.formBuilder.nonNullable.group({
    code: ['', [Validators.required, Validators.pattern(/^[A-Za-z0-9][A-Za-z0-9_-]{1,31}$/)]],
    name: ['', [Validators.required, Validators.maxLength(this.config.resource === 'units' ? 80 : 160)]],
    shortName: ['', [Validators.required, Validators.pattern(/^[A-Za-z0-9]{1,16}$/)]],
    description: ['', Validators.maxLength(this.config.resource === 'units' ? 500 : 1000)],
    decimalAllowed: [false],
    status: this.formBuilder.nonNullable.control<RecordStatus>('ACTIVE', Validators.required),
  });

  constructor() {
    if (!this.config.hasCode) {
      this.form.controls.code.clearValidators();
    }
    if (!this.config.hasShortName) {
      this.form.controls.shortName.clearValidators();
    }
    this.form.controls.code.updateValueAndValidity();
    this.form.controls.shortName.updateValueAndValidity();

    const idParam = this.route.snapshot.paramMap.get('id');
    const id = idParam ? Number(idParam) : null;
    if (idParam && !Number.isInteger(id)) {
      void this.router.navigate(['/', this.config.resource]);
      return;
    }
    if (id == null) {
      this.loading.set(false);
      return;
    }
    this.recordId.set(id);
    this.api.get(this.config.resource, id).subscribe({
      next: (record) => {
        this.version.set(record.version);
        this.form.patchValue({
          code: record.code ?? '',
          name: record.name,
          shortName: record.shortName ?? '',
          description: record.description ?? '',
          decimalAllowed: record.decimalAllowed ?? false,
          status: record.status,
        });
        this.form.markAsPristine();
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.loading.set(false);
        this.notifications.error(readApiError(error));
        void this.router.navigate(['/', this.config.resource]);
      },
    });
  }

  get editing(): boolean {
    return this.recordId() !== null;
  }

  get canSave(): boolean {
    return this.auth.hasPermission(this.editing ? this.config.updatePermission : this.config.createPermission);
  }

  hasUnsavedChanges(): boolean {
    return this.form.dirty && !this.saving();
  }

  @HostListener('window:beforeunload', ['$event'])
  protect(event: BeforeUnloadEvent): void {
    if (this.hasUnsavedChanges()) {
      event.preventDefault();
    }
  }

  invalid(name: 'code' | 'name' | 'shortName' | 'description' | 'status'): boolean {
    const control = this.form.controls[name];
    return control.invalid && (control.touched || this.form.touched);
  }

  submit(): void {
    if (!this.canSave || this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    const value = this.form.getRawValue();
    const body: CatalogWrite = {
      name: value.name.trim(),
      description: blankToNull(value.description),
      status: value.status,
    };
    if (this.config.hasCode) {
      body.code = value.code.trim();
    }
    if (this.config.hasShortName) {
      body.shortName = value.shortName.trim();
    }
    if (this.config.hasDecimal) {
      body.decimalAllowed = value.decimalAllowed;
    }
    const request = this.editing
      ? this.api.update(this.config.resource, this.recordId()!, { ...body, version: this.version() ?? 0 })
      : this.api.create(this.config.resource, body);
    request.subscribe({
      next: () => {
        this.form.markAsPristine();
        this.saving.set(false);
        const label = this.config.singular.charAt(0).toUpperCase() + this.config.singular.slice(1);
        this.notifications.success(this.editing ? 'Changes saved' : `${label} created`);
        void this.router.navigate(['/', this.config.resource]);
      },
      error: (error: unknown) => {
        this.saving.set(false);
        this.applyServerErrors(error);
        this.notifications.error(readApiError(error));
      },
    });
  }

  resetForm(): void {
    this.form.reset(this.form.getRawValue());
    if (this.editing && this.recordId() != null) {
      this.loading.set(true);
      this.api.get(this.config.resource, this.recordId()!).subscribe({
        next: (record) => {
          this.version.set(record.version);
          this.form.reset({
            code: record.code ?? '',
            name: record.name,
            shortName: record.shortName ?? '',
            description: record.description ?? '',
            decimalAllowed: record.decimalAllowed ?? false,
            status: record.status,
          });
          this.loading.set(false);
        },
        error: (error: unknown) => {
          this.loading.set(false);
          this.notifications.error(readApiError(error));
        },
      });
      return;
    }
    this.form.reset({
      code: '',
      name: '',
      shortName: '',
      description: '',
      decimalAllowed: false,
      status: 'ACTIVE',
    });
  }

  remove(): void {
    const id = this.recordId();
    if (id == null) {
      return;
    }
    this.confirmation
      .confirm({
        title: `Delete ${this.config.singular}`,
        message: 'Delete this record? It will disappear from new documents, and existing references stay intact when deletion is allowed.',
        confirmLabel: 'Delete',
        cancelLabel: 'Keep',
        destructive: true,
      })
      .subscribe((confirmed) => {
        if (!confirmed) {
          return;
        }
        this.api.delete(this.config.resource, id).subscribe({
          next: () => {
            this.form.markAsPristine();
            this.notifications.success('Record deleted');
            void this.router.navigate(['/', this.config.resource]);
          },
          error: (error: unknown) => this.notifications.error(readApiError(error)),
        });
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

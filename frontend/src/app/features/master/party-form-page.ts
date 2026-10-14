import { ChangeDetectionStrategy, Component, HostListener, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';
import { UnsavedForm } from '../../core/guards/unsaved-changes.guard';
import { PartyRecord, RecordStatus } from '../../core/models/master.model';
import { ConfirmationService } from '../../core/services/confirmation.service';
import { NotificationService } from '../../core/services/notification.service';
import { readApiError, readFieldErrors } from '../../core/utils/http-error.util';
import { FormSection } from '../../shared/components/form-section/form-section';
import { PageHeader } from '../../shared/components/page-header/page-header';
import { SkeletonLoader } from '../../shared/components/skeleton-loader/skeleton-loader';
import { blankToNull } from './master-format';
import { PartyApi, PartyWrite } from './party-api.service';
import { PartyConfig, partyConfig } from './party.config';

const EMPTY = {
  code: '',
  name: '',
  companyName: '',
  contactPerson: '',
  phone: '',
  alternatePhone: '',
  email: '',
  taxNumber: '',
  address: '',
  country: 'India',
  state: '',
  city: '',
  pincode: '',
  openingBalance: '0',
  creditLimit: '0',
  paymentTerms: '',
  status: 'ACTIVE' as RecordStatus,
};

@Component({
  selector: 'app-party-form-page',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    RouterLink,
    PageHeader,
    FormSection,
    SkeletonLoader,
  ],
  templateUrl: './party-form-page.html',
  styleUrl: './master-form.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PartyFormPage implements UnsavedForm {
  private readonly formBuilder = inject(FormBuilder);
  private readonly api = inject(PartyApi);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly notifications = inject(NotificationService);
  private readonly confirmation = inject(ConfirmationService);
  readonly auth = inject(AuthService);

  readonly config: PartyConfig = partyConfig(this.route.snapshot.data['resource']);
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly recordId = signal<number | null>(null);
  readonly version = signal<number | null>(null);

  readonly form = this.formBuilder.nonNullable.group({
    code: ['', [Validators.required, Validators.pattern(/^[A-Za-z0-9][A-Za-z0-9_-]{1,31}$/)]],
    name: ['', [Validators.required, Validators.maxLength(200)]],
    companyName: ['', Validators.maxLength(200)],
    contactPerson: ['', Validators.maxLength(160)],
    phone: ['', Validators.pattern(/^$|^[0-9+()\-\s]{7,32}$/)],
    alternatePhone: ['', Validators.pattern(/^$|^[0-9+()\-\s]{7,32}$/)],
    email: ['', Validators.pattern(/^$|^[^@\s]+@[^@\s]+\.[^@\s]+$/)],
    taxNumber: ['', Validators.maxLength(32)],
    address: ['', Validators.maxLength(500)],
    country: ['India', Validators.maxLength(80)],
    state: ['', Validators.maxLength(80)],
    city: ['', Validators.maxLength(80)],
    pincode: ['', Validators.pattern(/^$|^[A-Za-z0-9][A-Za-z0-9 -]{2,15}$/)],
    openingBalance: ['0', [Validators.required, Validators.pattern(/^-?\d+(\.\d{1,4})?$/)]],
    creditLimit: ['0', [Validators.required, Validators.pattern(/^\d+(\.\d{1,4})?$/)]],
    paymentTerms: ['', Validators.maxLength(120)],
    status: this.formBuilder.nonNullable.control<RecordStatus>('ACTIVE', Validators.required),
  });

  constructor() {
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
        this.fill(record);
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

  invalid(name: keyof typeof this.form.controls): boolean {
    const control = this.form.controls[name];
    return control.invalid && (control.touched || this.form.touched);
  }

  message(name: keyof typeof this.form.controls, fallback: string): string {
    const control = this.form.controls[name];
    return control.hasError('server') ? String(control.getError('server')) : fallback;
  }

  submit(): void {
    if (!this.canSave || this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    const value = this.form.getRawValue();
    const body: PartyWrite = {
      companyName: blankToNull(value.companyName),
      phone: blankToNull(value.phone),
      alternatePhone: blankToNull(value.alternatePhone),
      email: blankToNull(value.email),
      taxNumber: blankToNull(value.taxNumber),
      address: blankToNull(value.address),
      country: blankToNull(value.country),
      state: blankToNull(value.state),
      city: blankToNull(value.city),
      pincode: blankToNull(value.pincode),
      openingBalance: value.openingBalance,
      creditLimit: value.creditLimit,
      paymentTerms: blankToNull(value.paymentTerms),
      status: value.status,
      [this.config.codeField]: value.code.trim(),
      [this.config.nameField]: value.name.trim(),
    };
    if (this.config.hasContact) {
      body.contactPerson = blankToNull(value.contactPerson);
    }
    const request = this.editing
      ? this.api.update(this.config.resource, this.recordId()!, { ...body, version: this.version() ?? 0 })
      : this.api.create(this.config.resource, body);
    request.subscribe({
      next: (saved) => {
        this.form.markAsPristine();
        this.saving.set(false);
        this.notifications.success(this.editing ? 'Changes saved' : 'Record created');
        void this.router.navigate(['/', this.config.resource, saved.id]);
      },
      error: (error: unknown) => {
        this.saving.set(false);
        for (const detail of readFieldErrors(error)) {
          if (detail.field === this.config.codeField) {
            this.form.controls.code.setErrors({ server: detail.message });
          } else if (detail.field === this.config.nameField) {
            this.form.controls.name.setErrors({ server: detail.message });
          } else if (detail.field) {
            this.form.get(detail.field)?.setErrors({ server: detail.message });
          }
        }
        this.notifications.error(readApiError(error));
      },
    });
  }

  resetForm(): void {
    const id = this.recordId();
    if (id == null) {
      this.form.reset(EMPTY);
      return;
    }
    this.loading.set(true);
    this.api.get(this.config.resource, id).subscribe({
      next: (record) => {
        this.fill(record);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.loading.set(false);
        this.notifications.error(readApiError(error));
      },
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
        message: 'Delete this party? Historical transactions must remain, so a party with posted activity should be set inactive.',
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

  private fill(record: PartyRecord): void {
    this.version.set(record.version);
    this.form.reset({
      code: record[this.config.codeField] ?? '',
      name: record[this.config.nameField] ?? '',
      companyName: record.companyName ?? '',
      contactPerson: record.contactPerson ?? '',
      phone: record.phone ?? '',
      alternatePhone: record.alternatePhone ?? '',
      email: record.email ?? '',
      taxNumber: record.taxNumber ?? '',
      address: record.address ?? '',
      country: record.country ?? '',
      state: record.state ?? '',
      city: record.city ?? '',
      pincode: record.pincode ?? '',
      openingBalance: String(record.openingBalance),
      creditLimit: String(record.creditLimit),
      paymentTerms: record.paymentTerms ?? '',
      status: record.status,
    });
  }
}

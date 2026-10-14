import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';

import { AuthService } from '../../core/auth/auth.service';
import { UnsavedForm } from '../../core/guards/unsaved-changes.guard';
import { ConfirmationService } from '../../core/services/confirmation.service';
import { SystemSnapshot } from '../../core/services/system-snapshot.service';
import { readApiError } from '../../core/utils/http-error.util';
import { configureDisplay } from '../master/master-format';
import { FormSection } from '../../shared/components/form-section/form-section';
import { PageHeader } from '../../shared/components/page-header/page-header';
import { SkeletonLoader } from '../../shared/components/skeleton-loader/skeleton-loader';
import { DataResetDialog } from './data-reset-dialog';
import { SettingsApi } from './settings-api.service';
import { CURRENCIES, DATE_FORMATS, PAYMENT_DEFAULTS, ROUNDING_RULES, BusinessSettings } from './settings.model';

@Component({
  selector: 'app-settings-page',
  imports: [
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    PageHeader,
    FormSection,
    SkeletonLoader,
  ],
  templateUrl: './settings-page.html',
  styleUrls: ['./settings-page.scss', '../master/master-form.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SettingsPage implements UnsavedForm {
  private readonly api = inject(SettingsApi);
  private readonly dialog = inject(MatDialog);
  private readonly confirmation = inject(ConfirmationService);
  private readonly snapshot = inject(SystemSnapshot);
  readonly auth = inject(AuthService);

  readonly currencies = CURRENCIES;
  readonly roundingRules = ROUNDING_RULES;
  readonly dateFormats = DATE_FORMATS;
  readonly paymentMethods = PAYMENT_DEFAULTS;
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly dirty = signal(false);
  readonly status = signal('');
  readonly errorMessage = signal('');
  readonly fieldErrors = signal<Record<string, string>>({});
  readonly form = signal<BusinessSettings | null>(null);
  private original = '';

  constructor() {
    this.api.current().subscribe({
      next: (settings) => {
        this.form.set(settings);
        this.original = JSON.stringify(settings);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.errorMessage.set(readApiError(error));
        this.loading.set(false);
      },
    });
  }

  hasUnsavedChanges(): boolean {
    return this.dirty() && !this.saving();
  }

  canUpdate(): boolean {
    return this.auth.hasPermission('SETTINGS_UPDATE');
  }

  openReset(): void {
    if (!this.canUpdate()) {
      return;
    }
    this.dialog
      .open(DataResetDialog, {
        width: '640px',
        maxWidth: 'calc(100vw - 32px)',
        autoFocus: 'first-tabbable',
        restoreFocus: true,
      })
      .afterClosed()
      .subscribe((deletedRows: number | undefined) => {
        if (typeof deletedRows !== 'number') {
          return;
        }
        this.errorMessage.set('');
        this.status.set(deletedRows === 1 ? 'Deleted 1 row' : `Deleted ${deletedRows} rows`);
      });
  }

  setPrecision(value: string): void {
    const parsed = Number(value);
    this.patch({ decimalPrecision: Number.isNaN(parsed) ? 0 : parsed });
  }

  patch(partial: Partial<BusinessSettings>): void {
    const current = this.form();
    if (!current || !this.canUpdate()) {
      return;
    }
    const next = { ...current, ...partial };
    this.form.set(next);
    this.dirty.set(JSON.stringify(next) !== this.original);
    this.status.set(this.dirty() ? 'Unsaved changes' : '');
    this.fieldErrors.set(this.validate(next));
  }

  save(): void {
    const current = this.form();
    if (!current || !this.canUpdate()) {
      return;
    }
    const errors = this.validate(current);
    this.fieldErrors.set(errors);
    if (Object.keys(errors).length > 0) {
      this.status.set('Fix the highlighted fields');
      return;
    }
    const original = JSON.parse(this.original) as BusinessSettings;
    const sensitive =
      original.allowNegativeStock !== current.allowNegativeStock ||
      original.invoicePrefix !== current.invoicePrefix ||
      original.purchasePrefix !== current.purchasePrefix ||
      original.returnPrefix !== current.returnPrefix ||
      original.purchaseReturnPrefix !== current.purchaseReturnPrefix ||
      original.roundingRule !== current.roundingRule ||
      original.decimalPrecision !== Number(current.decimalPrecision) ||
      original.currencyCode !== current.currencyCode;
    const proceed = sensitive
      ? this.confirmation.confirm({
          title: 'Save business rules',
          message: 'This changes document numbering, stock rules, currency, or rounding. Save these settings?',
          confirmLabel: 'Save settings',
          cancelLabel: 'Keep editing',
          destructive: false,
        })
      : null;
    if (proceed) {
      proceed.subscribe((confirmed) => {
        if (confirmed) {
          this.persist(current);
        }
      });
      return;
    }
    this.persist(current);
  }

  private persist(current: BusinessSettings): void {
    this.saving.set(true);
    this.status.set('Saving…');
    this.errorMessage.set('');
    const body = { ...current, decimalPrecision: Number(current.decimalPrecision) };
    this.api.save(body).subscribe({
      next: (saved) => {
        this.form.set(saved);
        this.original = JSON.stringify(saved);
        this.dirty.set(false);
        this.saving.set(false);
        this.status.set('Saved');
        configureDisplay({
          locale: 'en-IN',
          currency: saved.currencyCode,
          digits: saved.decimalPrecision,
          rounding: saved.roundingRule,
          dateFormat: saved.dateFormat,
        });
        this.snapshot.load(true);
      },
      error: (error: unknown) => {
        this.saving.set(false);
        this.status.set('');
        this.errorMessage.set(readApiError(error));
      },
    });
  }

  private validate(settings: BusinessSettings): Record<string, string> {
    const errors: Record<string, string> = {};
    if (!settings.companyName.trim()) {
      errors['companyName'] = 'Company name is required';
    }
    if (settings.email.trim() && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(settings.email.trim())) {
      errors['email'] = 'Enter a valid email address';
    }
    if (!/^[A-Z]{3}$/.test(settings.currencyCode.trim().toUpperCase())) {
      errors['currencyCode'] = 'Use a 3-letter currency code';
    }
    for (const field of ['invoicePrefix', 'purchasePrefix', 'returnPrefix', 'purchaseReturnPrefix'] as const) {
      if (!/^[A-Za-z0-9]{2,8}$/.test(settings[field].trim())) {
        errors[field] = 'Use 2 to 8 letters or digits';
      }
    }
    if (settings.decimalPrecision < 0 || settings.decimalPrecision > 4) {
      errors['decimalPrecision'] = 'Precision must be from 0 to 4';
    }
    return errors;
  }
}

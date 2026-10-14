import { Injectable, inject, signal } from '@angular/core';
import { forkJoin } from 'rxjs';

import { configureDisplay } from '../../features/master/master-format';
import { CommercialDefaults, SystemHealth, SystemInfo } from '../models/system.model';
import { readApiError } from '../utils/http-error.util';
import { SystemApi } from './system-api.service';

@Injectable({ providedIn: 'root' })
export class SystemSnapshot {
  private readonly api = inject(SystemApi);

  readonly info = signal<SystemInfo | null>(null);
  readonly health = signal<SystemHealth | null>(null);
  readonly preferences = signal<CommercialDefaults | null>(null);
  readonly error = signal<string | null>(null);
  readonly loaded = signal(false);
  private inflight = false;

  load(force = false): void {
    if (this.inflight || (!force && this.loaded())) {
      return;
    }
    this.inflight = true;
    this.error.set(null);
    forkJoin({
      info: this.api.info(),
      health: this.api.health(),
      preferences: this.api.preferences(),
    }).subscribe({
      next: (result) => {
        this.info.set(result.info);
        this.health.set(result.health);
        this.preferences.set(result.preferences);
        configureDisplay({
          locale: result.preferences.locale || 'en-IN',
          currency: result.preferences.currencyCode || 'INR',
          digits: result.preferences.decimalPrecision ?? 2,
          rounding: result.preferences.roundingRule ?? 'NONE',
          dateFormat: result.preferences.dateFormat ?? 'medium',
        });
        this.inflight = false;
        this.loaded.set(true);
        this.error.set(null);
      },
      error: (error: unknown) => {
        this.inflight = false;
        this.loaded.set(false);
        this.error.set(readApiError(error));
      },
    });
  }
}

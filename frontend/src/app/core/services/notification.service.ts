import { Injectable, signal } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';

export type AlertLevel = 'success' | 'info' | 'warning' | 'error';

export interface AlertItem {
  id: number;
  level: AlertLevel;
  message: string;
  at: string;
}

@Injectable({ providedIn: 'root' })
export class NotificationService {
  private nextId = 1;
  readonly alerts = signal<readonly AlertItem[]>([]);

  constructor(private readonly snackBar: MatSnackBar) {}

  success(message: string): void {
    this.publish('success', message, 4000);
  }

  info(message: string): void {
    this.publish('info', message, 4000);
  }

  warning(message: string): void {
    this.publish('warning', message, 5000);
  }

  error(message: string): void {
    this.publish('error', message, 6000);
  }

  dismiss(id: number): void {
    this.alerts.update((items) => items.filter((item) => item.id !== id));
  }

  private publish(level: AlertLevel, message: string, duration: number): void {
    const item: AlertItem = {
      id: this.nextId++,
      level,
      message,
      at: new Date().toISOString(),
    };
    this.alerts.update((items) => [item, ...items].slice(0, 8));
    this.snackBar.open(message, 'Dismiss', {
      duration,
      horizontalPosition: 'end',
      verticalPosition: 'top',
      politeness: level === 'error' ? 'assertive' : 'polite',
      panelClass: ['bf-snackbar', `bf-snackbar-${level}`],
    });
  }
}

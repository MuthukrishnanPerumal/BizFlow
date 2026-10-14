import { Pipe, PipeTransform } from '@angular/core';

@Pipe({ name: 'appCurrency' })
export class AppCurrencyPipe implements PipeTransform {
  transform(amount: number | null | undefined, currency = 'INR', locale = 'en-IN'): string {
    if (amount === null || amount === undefined || Number.isNaN(amount)) {
      return '—';
    }
    try {
      return new Intl.NumberFormat(locale, {
        style: 'currency',
        currency,
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
      }).format(amount);
    } catch {
      return amount.toFixed(2);
    }
  }
}

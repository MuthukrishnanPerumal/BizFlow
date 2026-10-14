import { signal } from '@angular/core';

interface DisplaySettings {
  locale: string;
  currency: string;
  digits: number;
  rounding: string;
  dateFormat: string;
}

const formatVersion = signal(0);
const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

let display: DisplaySettings = {
  locale: 'en-IN',
  currency: 'INR',
  digits: 2,
  rounding: 'NONE',
  dateFormat: 'medium',
};

export function configureDisplay(next: Partial<DisplaySettings>): void {
  display = { ...display, ...next };
  if (display.digits < 0 || display.digits > 4 || Number.isNaN(display.digits)) {
    display.digits = 2;
  }
  formatVersion.update((value) => value + 1);
}

export function dateFormatVersion(): number {
  return formatVersion();
}

export function dateInputHint(): string {
  switch (display.dateFormat) {
    case 'dd/MM/yyyy':
      return 'dd/mm/yyyy';
    case 'MM/dd/yyyy':
      return 'mm/dd/yyyy';
    case 'yyyy-MM-dd':
      return 'yyyy-mm-dd';
    default:
      return 'dd mmm yyyy';
  }
}

export function toIsoDate(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

export function parseUserDate(value: string): Date | null {
  const text = value.trim();
  const iso = /^(\d{4})-(\d{2})-(\d{2})$/.exec(text);
  if (iso) {
    return calendarDate(Number(iso[1]), Number(iso[2]), Number(iso[3]));
  }

  const numeric = /^(\d{1,2})[\/\-.](\d{1,2})[\/\-.](\d{4})$/.exec(text);
  if (numeric) {
    const first = Number(numeric[1]);
    const second = Number(numeric[2]);
    const year = Number(numeric[3]);
    if (display.dateFormat === 'MM/dd/yyyy') {
      return calendarDate(year, first, second);
    }
    return calendarDate(year, second, first);
  }

  const named = /^(\d{1,2})\s+([A-Za-z]{3,})\s+(\d{4})$/.exec(text);
  if (!named) {
    return null;
  }
  const month = MONTHS.findIndex((item) => named[2].toLowerCase().startsWith(item.toLowerCase()));
  if (month < 0) {
    return null;
  }
  return calendarDate(Number(named[3]), month + 1, Number(named[1]));
}

function calendarDate(year: number, month: number, day: number): Date | null {
  const date = new Date(year, month - 1, day);
  if (date.getFullYear() !== year || date.getMonth() !== month - 1 || date.getDate() !== day) {
    return null;
  }
  date.setHours(0, 0, 0, 0);
  return date;
}

export function formatMoney(value: string | number | null | undefined): string {
  let amount = typeof value === 'number' ? value : Number(value ?? 0);
  if (Number.isNaN(amount)) {
    return '—';
  }
  if (display.rounding === 'NEAREST_UNIT') {
    amount = Math.round(amount);
  } else if (display.rounding === 'NEAREST_TENTH') {
    amount = Math.round(amount * 10) / 10;
  }
  return new Intl.NumberFormat(display.locale, {
    style: 'currency',
    currency: display.currency,
    minimumFractionDigits: display.digits,
    maximumFractionDigits: display.digits,
  }).format(amount);
}

export function formatWhen(value: string | null | undefined): string {
  if (!value) {
    return '—';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  const time = new Intl.DateTimeFormat(display.locale, { timeStyle: 'short' }).format(date);
  return `${formatDateValue(date)} ${time}`;
}

export function formatDate(value: string | null | undefined): string {
  if (!value) {
    return '—';
  }
  const date = new Date(`${value}T00:00:00`);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return formatDateValue(date);
}

function formatDateValue(date: Date): string {
  if (display.dateFormat === 'medium') {
    return new Intl.DateTimeFormat(display.locale, { dateStyle: 'medium' }).format(date);
  }
  const day = String(date.getDate()).padStart(2, '0');
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const year = String(date.getFullYear());
  switch (display.dateFormat) {
    case 'dd/MM/yyyy':
      return `${day}/${month}/${year}`;
    case 'MM/dd/yyyy':
      return `${month}/${day}/${year}`;
    case 'dd MMM yyyy':
      return `${day} ${MONTHS[date.getMonth()]} ${year}`;
    default:
      return `${year}-${month}-${day}`;
  }
}

export function blankToNull(value: string): string | null {
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : null;
}

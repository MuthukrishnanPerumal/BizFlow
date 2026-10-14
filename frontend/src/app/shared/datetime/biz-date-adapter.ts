import { Injectable } from '@angular/core';
import { NativeDateAdapter } from '@angular/material/core';

import { formatDate, parseUserDate, toIsoDate } from '../../features/master/master-format';

@Injectable()
export class BizDateAdapter extends NativeDateAdapter {
  override parse(value: unknown, _parseFormat?: unknown): Date | null {
    if (typeof value === 'string') {
      const trimmed = value.trim();
      return trimmed ? parseUserDate(trimmed) : null;
    }
    if (value instanceof Date) {
      return Number.isNaN(value.getTime()) ? null : value;
    }
    if (typeof value === 'number') {
      const date = new Date(value);
      return Number.isNaN(date.getTime()) ? null : date;
    }
    return null;
  }

  override format(date: Date, displayFormat: { month?: string; day?: string }): string {
    if (displayFormat?.month === 'numeric' && displayFormat?.day === 'numeric') {
      const formatted = formatDate(toIsoDate(date));
      return formatted === '—' ? '' : formatted;
    }
    return super.format(date, displayFormat);
  }

  override deserialize(value: unknown): Date | null {
    if (typeof value === 'string') {
      const trimmed = value.trim();
      if (!trimmed) {
        return null;
      }
      if (/^\d{4}-\d{2}-\d{2}/.test(trimmed)) {
        return parseUserDate(trimmed.slice(0, 10));
      }
    }
    return super.deserialize(value);
  }
}

import { HttpErrorResponse } from '@angular/common/http';

import { ApiResponse, FieldErrorDetail } from '../models/api-response.model';

export function readApiError(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0) {
      return 'The server could not be reached. Check your connection and try again.';
    }
    const message = readBodyMessage(error.error);
    if (message) {
      return message;
    }
    if (error.status === 401) {
      return 'Your session has ended. Sign in again to continue.';
    }
    if (error.status === 403) {
      return 'You do not have permission for this. Ask an administrator if you need access.';
    }
    if (error.status === 404) {
      return 'That record is no longer available. Go back and refresh the list.';
    }
    return 'This could not be completed. Try again in a moment.';
  }
  if (error instanceof Error && error.message) {
    return error.message;
  }
  return 'Request could not be completed';
}

export function explainError(error: unknown): string {
  const details = readFieldErrors(error)
    .map((item) => item.message.trim())
    .filter((message) => message.length > 0 && message !== 'Validation failed');
  if (details.length > 0) {
    return details.join(' ');
  }
  return readApiError(error);
}

export function readFieldErrors(error: unknown): FieldErrorDetail[] {
  if (!(error instanceof HttpErrorResponse) || !error.error || typeof error.error !== 'object') {
    return [];
  }
  const errors = (error.error as Partial<ApiResponse<unknown>>).errors;
  return Array.isArray(errors) ? errors.filter((item) => !!item && typeof item.message === 'string') : [];
}

function readBodyMessage(body: unknown): string | null {
  if (!body || typeof body !== 'object') {
    return null;
  }
  const candidate = body as Partial<ApiResponse<unknown>>;
  return typeof candidate.message === 'string' && candidate.message.trim().length > 0
    ? candidate.message
    : null;
}

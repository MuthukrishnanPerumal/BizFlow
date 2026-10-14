import { HttpErrorResponse } from '@angular/common/http';

import { explainError, readApiError, readFieldErrors } from './http-error.util';

describe('readApiError', () => {
  it('uses the server message when the body is an API failure', () => {
    const error = new HttpErrorResponse({
      status: 422,
      error: { success: false, message: 'Quantity exceeds available stock', errors: [] },
    });

    expect(readApiError(error)).toBe('Quantity exceeds available stock');
  });

  it('hides transport and authorization failures behind stable messages', () => {
    expect(readApiError(new HttpErrorResponse({ status: 0, error: null }))).toBe(
      'The server could not be reached. Check your connection and try again.',
    );
    expect(readApiError(new HttpErrorResponse({ status: 401, error: null }))).toBe(
      'Your session has ended. Sign in again to continue.',
    );
    expect(readApiError(new HttpErrorResponse({ status: 403, error: { message: '   ' } }))).toBe(
      'You do not have permission for this. Ask an administrator if you need access.',
    );
  });

  it('reads field errors and ignores malformed bodies', () => {
    const error = new HttpErrorResponse({
      status: 400,
      error: {
        errors: [
          { field: 'quantity', code: 'Positive', message: 'Quantity must be greater than zero' },
          { field: 'price', code: 'broken' },
        ],
      },
    });

    expect(readFieldErrors(error)).toEqual([
      { field: 'quantity', code: 'Positive', message: 'Quantity must be greater than zero' },
    ]);
    expect(readFieldErrors(new HttpErrorResponse({ status: 500, error: 'down' }))).toEqual([]);
  });
});

describe('explainError', () => {
  it('prefers the field message when the response only says validation failed', () => {
    const error = new HttpErrorResponse({
      status: 400,
      error: {
        message: 'Validation failed',
        errors: [{ field: 'quantity', code: 'INSUFFICIENT_STOCK', message: 'Notebook does not have enough stock' }],
      },
    });

    expect(explainError(error)).toBe('Notebook does not have enough stock');
  });
});

import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

import { AuthSession } from '../auth/auth-session.service';
import { SKIP_AUTH_LOGOUT, SKIP_ERROR_NOTIFICATION } from '../http/http-context';
import { NotificationService } from '../services/notification.service';
import { readApiError } from '../utils/http-error.util';

export const errorInterceptor: HttpInterceptorFn = (request, next) => {
  const notifications = inject(NotificationService);
  const session = inject(AuthSession);
  const router = inject(Router);
  return next(request).pipe(
    catchError((error: unknown) => {
      if (
        error instanceof HttpErrorResponse &&
        error.status === 401 &&
        !request.context.get(SKIP_AUTH_LOGOUT) &&
        session.isAuthenticated()
      ) {
        session.clear();
        void router.navigate(['/login']);
      }
      if (!request.context.get(SKIP_ERROR_NOTIFICATION)) {
        notifications.error(readApiError(error));
      }
      return throwError(() => error);
    }),
  );
};

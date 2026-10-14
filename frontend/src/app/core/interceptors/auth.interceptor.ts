import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';

import { environment } from '../../../environments/environment';
import { AuthSession } from '../auth/auth-session.service';

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const token = inject(AuthSession).accessToken();
  if (!token || !request.url.startsWith(environment.apiUrl)) {
    return next(request);
  }
  return next(
    request.clone({
      setHeaders: { Authorization: `Bearer ${token}` },
    }),
  );
};

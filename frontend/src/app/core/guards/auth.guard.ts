import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { environment } from '../../../environments/environment';
import { AuthSession } from '../auth/auth-session.service';

export const authGuard: CanActivateFn = (_route, state) => {
  if (!environment.authRequired) {
    return true;
  }
  const session = inject(AuthSession);
  const router = inject(Router);
  if (!session.isAuthenticated()) {
    return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
  }
  const path = state.url.split('?')[0] ?? '';
  if (session.user()?.mustChangePassword && path !== '/account/password') {
    return router.createUrlTree(['/account/password']);
  }
  return true;
};

export const guestGuard: CanActivateFn = () => {
  const session = inject(AuthSession);
  const router = inject(Router);
  if (!session.isAuthenticated()) {
    return true;
  }
  if (session.user()?.mustChangePassword) {
    return router.createUrlTree(['/account/password']);
  }
  return router.createUrlTree(['/dashboard']);
};

export const permissionGuard: CanActivateFn = (route) => {
  const required = route.data['permission'];
  if (typeof required !== 'string' || required.length === 0) {
    return true;
  }
  const session = inject(AuthSession);
  if (session.hasPermission(required)) {
    return true;
  }
  return inject(Router).createUrlTree(['/forbidden']);
};

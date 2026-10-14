import { HttpClient, HttpContext } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, finalize, firstValueFrom, map, of, tap } from 'rxjs';

import { environment } from '../../../environments/environment';
import { SKIP_AUTH_LOGOUT, SKIP_ERROR_NOTIFICATION, SKIP_LOADING_INDICATOR } from '../http/http-context';
import { unwrapData } from '../http/unwrap';
import { CurrentUser, LoginResult } from '../models/identity.model';
import { ApiResponse } from '../models/api-response.model';
import { SessionUser } from '../models/session.model';
import { AuthSession } from './auth-session.service';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly session = inject(AuthSession);

  readonly user = this.session.user;
  readonly isAuthenticated = this.session.isAuthenticated;

  login(username: string, password: string): Observable<SessionUser> {
    return this.http
      .post<ApiResponse<LoginResult>>(`${environment.apiUrl}/auth/login`, { username, password }, { context: quietContext() })
      .pipe(
        map((response) => {
          const data = unwrapData(response);
          const sessionUser = toSession(data.user);
          this.session.establish(data.accessToken, sessionUser);
          return sessionUser;
        }),
      );
  }

  logout(): Observable<void> {
    const token = this.session.accessToken();
    const request: Observable<void> = token
      ? this.http
          .post<ApiResponse<unknown>>(`${environment.apiUrl}/auth/logout`, {}, { context: quietContext() })
          .pipe(
            map(() => undefined),
            catchError(() => of(undefined)),
          )
      : of(undefined);
    return request.pipe(finalize(() => this.session.clear()));
  }

  changePassword(currentPassword: string, newPassword: string): Observable<SessionUser> {
    return this.http
      .post<ApiResponse<LoginResult>>(
        `${environment.apiUrl}/auth/change-password`,
        { currentPassword, newPassword },
        { context: quietContext() },
      )
      .pipe(
        map((response) => {
          const data = unwrapData(response);
          const sessionUser = toSession(data.user);
          this.session.establish(data.accessToken, sessionUser);
          return sessionUser;
        }),
      );
  }

  restore(): Promise<void> {
    if (!this.session.isAuthenticated()) {
      return Promise.resolve();
    }
    return firstValueFrom(
      this.currentUser().pipe(
        tap((user) => this.session.replaceProfile(user)),
        catchError(() => {
          this.session.clear();
          return of(null);
        }),
      ),
    ).then(() => undefined);
  }

  accessToken(): string | null {
    return this.session.accessToken();
  }

  hasPermission(permission: string): boolean {
    return this.session.hasPermission(permission);
  }

  private currentUser(): Observable<SessionUser> {
    return this.http
      .get<ApiResponse<CurrentUser>>(`${environment.apiUrl}/auth/me`, { context: quietContext() })
      .pipe(map((response) => toSession(unwrapData(response))));
  }
}

function quietContext(): HttpContext {
  return new HttpContext()
    .set(SKIP_ERROR_NOTIFICATION, true)
    .set(SKIP_LOADING_INDICATOR, true)
    .set(SKIP_AUTH_LOGOUT, true);
}

export function toSession(user: CurrentUser): SessionUser {
  return {
    id: user.id,
    username: user.username,
    fullName: user.fullName,
    email: user.email,
    phone: user.phone ?? null,
    status: user.status,
    roles: (user.roles ?? []).map((role) => role.code),
    permissions: user.permissions ?? [],
    lastLoginAt: user.lastLoginAt ?? null,
    mustChangePassword: user.mustChangePassword,
  };
}

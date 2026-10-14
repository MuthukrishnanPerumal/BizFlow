import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { environment } from '../../../environments/environment';
import { SKIP_ERROR_NOTIFICATION } from '../../core/http/http-context';
import { unwrapData } from '../../core/http/unwrap';
import { PageResult, UserRecord, UserWrite } from '../../core/models/identity.model';
import { ApiResponse } from '../../core/models/api-response.model';

@Injectable({ providedIn: 'root' })
export class UserApi {
  private readonly http = inject(HttpClient);

  list(search: string, status: string, page: number): Observable<PageResult<UserRecord>> {
    let params = new HttpParams().set('page', page).set('size', 20);
    if (search) {
      params = params.set('search', search);
    }
    if (status) {
      params = params.set('status', status);
    }
    return this.http
      .get<ApiResponse<PageResult<UserRecord>>>(`${environment.apiUrl}/users`, { params })
      .pipe(map((response) => unwrapData(response)));
  }

  get(id: number): Observable<UserRecord> {
    return this.http
      .get<ApiResponse<UserRecord>>(`${environment.apiUrl}/users/${id}`)
      .pipe(map((response) => unwrapData(response)));
  }

  create(body: UserWrite): Observable<UserRecord> {
    return this.http
      .post<ApiResponse<UserRecord>>(`${environment.apiUrl}/users`, body, { context: quiet() })
      .pipe(map((response) => unwrapData(response)));
  }

  update(id: number, body: UserWrite): Observable<UserRecord> {
    return this.http
      .put<ApiResponse<UserRecord>>(`${environment.apiUrl}/users/${id}`, body, { context: quiet() })
      .pipe(map((response) => unwrapData(response)));
  }

  delete(id: number): Observable<void> {
    return this.http
      .delete<ApiResponse<unknown>>(`${environment.apiUrl}/users/${id}`, { context: quiet() })
      .pipe(map((response) => {
        if (!response.success) {
          throw new Error(response.message || 'Request could not be completed');
        }
      }));
  }
}

function quiet(): HttpContext {
  return new HttpContext().set(SKIP_ERROR_NOTIFICATION, true);
}

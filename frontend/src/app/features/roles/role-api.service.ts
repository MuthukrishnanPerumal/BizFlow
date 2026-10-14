import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { environment } from '../../../environments/environment';
import { SKIP_ERROR_NOTIFICATION } from '../../core/http/http-context';
import { unwrapData } from '../../core/http/unwrap';
import { PageResult, PermissionGroup, RoleRecord } from '../../core/models/identity.model';
import { ApiResponse } from '../../core/models/api-response.model';

export interface RoleWrite {
  name: string;
  description: string | null;
  permissionIds: number[];
  version?: number;
  code?: string;
}

@Injectable({ providedIn: 'root' })
export class RoleApi {
  private readonly http = inject(HttpClient);

  list(search = '', page = 0, size = 50): Observable<PageResult<RoleRecord>> {
    let params = new HttpParams().set('page', page).set('size', size).set('sort', 'name,asc');
    if (search) {
      params = params.set('search', search);
    }
    return this.http
      .get<ApiResponse<PageResult<RoleRecord>>>(`${environment.apiUrl}/roles`, { params })
      .pipe(map((response) => unwrapData(response)));
  }

  get(id: number): Observable<RoleRecord> {
    return this.http
      .get<ApiResponse<RoleRecord>>(`${environment.apiUrl}/roles/${id}`)
      .pipe(map((response) => unwrapData(response)));
  }

  create(body: RoleWrite): Observable<RoleRecord> {
    return this.http
      .post<ApiResponse<RoleRecord>>(`${environment.apiUrl}/roles`, body, { context: quiet() })
      .pipe(map((response) => unwrapData(response)));
  }

  update(id: number, body: RoleWrite): Observable<RoleRecord> {
    return this.http
      .put<ApiResponse<RoleRecord>>(`${environment.apiUrl}/roles/${id}`, body, { context: quiet() })
      .pipe(map((response) => unwrapData(response)));
  }

  delete(id: number): Observable<void> {
    return this.http
      .delete<ApiResponse<unknown>>(`${environment.apiUrl}/roles/${id}`, { context: quiet() })
      .pipe(map((response) => {
        if (!response.success) {
          throw new Error(response.message || 'Request could not be completed');
        }
      }));
  }

  permissions(): Observable<PermissionGroup[]> {
    return this.http
      .get<ApiResponse<{ groups: PermissionGroup[] }>>(`${environment.apiUrl}/permissions`)
      .pipe(map((response) => unwrapData(response).groups));
  }
}

function quiet(): HttpContext {
  return new HttpContext().set(SKIP_ERROR_NOTIFICATION, true);
}

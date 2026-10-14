import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { environment } from '../../../environments/environment';
import { SKIP_ERROR_NOTIFICATION } from '../../core/http/http-context';
import { unwrapData } from '../../core/http/unwrap';
import { ApiResponse } from '../../core/models/api-response.model';
import { CatalogRecord, MasterPage, RecordStatus } from '../../core/models/master.model';
import { CatalogResource } from './catalog.config';

export interface CatalogWrite {
  code?: string;
  name: string;
  shortName?: string;
  description: string | null;
  decimalAllowed?: boolean;
  status: RecordStatus;
  version?: number;
}

@Injectable({ providedIn: 'root' })
export class CatalogApi {
  private readonly http = inject(HttpClient);

  list(
    resource: CatalogResource,
    search: string,
    status: string,
    page: number,
    sort: string,
    size = 20,
  ): Observable<MasterPage<CatalogRecord>> {
    let params = new HttpParams().set('page', page).set('size', size).set('sort', sort);
    if (search) {
      params = params.set('search', search);
    }
    if (status) {
      params = params.set('status', status);
    }
    return this.http
      .get<ApiResponse<MasterPage<CatalogRecord>>>(`${environment.apiUrl}/${resource}`, { params, context: quiet() })
      .pipe(map((response) => unwrapData(response)));
  }

  get(resource: CatalogResource, id: number): Observable<CatalogRecord> {
    return this.http
      .get<ApiResponse<CatalogRecord>>(`${environment.apiUrl}/${resource}/${id}`, { context: quiet() })
      .pipe(map((response) => unwrapData(response)));
  }

  create(resource: CatalogResource, body: CatalogWrite): Observable<CatalogRecord> {
    return this.http
      .post<ApiResponse<CatalogRecord>>(`${environment.apiUrl}/${resource}`, body, { context: quiet() })
      .pipe(map((response) => unwrapData(response)));
  }

  update(resource: CatalogResource, id: number, body: CatalogWrite): Observable<CatalogRecord> {
    return this.http
      .put<ApiResponse<CatalogRecord>>(`${environment.apiUrl}/${resource}/${id}`, body, { context: quiet() })
      .pipe(map((response) => unwrapData(response)));
  }

  delete(resource: CatalogResource, id: number): Observable<void> {
    return this.http
      .delete<ApiResponse<unknown>>(`${environment.apiUrl}/${resource}/${id}`, { context: quiet() })
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

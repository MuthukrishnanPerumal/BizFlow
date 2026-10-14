import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { environment } from '../../../environments/environment';
import { unwrapData } from '../../core/http/unwrap';
import { ApiResponse } from '../../core/models/api-response.model';
import { AuditDetail, AuditPage, AuditQuery } from './audit.model';

@Injectable({ providedIn: 'root' })
export class AuditApi {
  private readonly http = inject(HttpClient);

  search(query: AuditQuery): Observable<AuditPage> {
    return this.http
      .get<ApiResponse<AuditPage>>(`${environment.apiUrl}/audit`, { params: filters(query).set('page', query.page).set('size', 20) })
      .pipe(map((response) => unwrapData(response)));
  }

  get(id: number): Observable<AuditDetail> {
    return this.http
      .get<ApiResponse<AuditDetail>>(`${environment.apiUrl}/audit/${id}`)
      .pipe(map((response) => unwrapData(response)));
  }

  export(format: 'csv' | 'xlsx' | 'pdf', query: AuditQuery): Observable<Blob> {
    return this.http.get(`${environment.apiUrl}/audit/export`, {
      params: filters(query).set('format', format),
      responseType: 'blob',
    });
  }
}

function filters(query: AuditQuery): HttpParams {
  let params = new HttpParams();
  if (query.from) {
    params = params.set('from', query.from);
  }
  if (query.to) {
    params = params.set('to', query.to);
  }
  if (query.user.trim()) {
    params = params.set('user', query.user.trim());
  }
  if (query.module) {
    params = params.set('module', query.module);
  }
  if (query.action) {
    params = params.set('action', query.action);
  }
  if (query.entity.trim()) {
    params = params.set('entity', query.entity.trim());
  }
  if (query.search.trim()) {
    params = params.set('search', query.search.trim());
  }
  return params;
}

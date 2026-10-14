import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { environment } from '../../../environments/environment';
import { unwrapData } from '../../core/http/unwrap';
import { ApiResponse } from '../../core/models/api-response.model';
import {
  DashboardResponse,
  DocumentScope,
  PeriodQuery,
  ReportPageResult,
  ReportRow,
} from './reporting.model';

@Injectable({ providedIn: 'root' })
export class ReportingApi {
  private readonly http = inject(HttpClient);

  dashboard(query: PeriodQuery): Observable<DashboardResponse> {
    return this.http
      .get<ApiResponse<DashboardResponse>>(`${environment.apiUrl}/dashboard`, { params: periodParams(query) })
      .pipe(map((response) => unwrapData(response)));
  }

  report(
    kind: 'sales' | 'purchases' | 'inventory' | 'profit',
    query: PeriodQuery,
    options: { search: string; scope: DocumentScope; page: number; size: number; sort: string },
  ): Observable<ReportPageResult<ReportRow>> {
    return this.http
      .get<ApiResponse<ReportPageResult<ReportRow>>>(`${environment.apiUrl}/reports/${kind}`, {
        params: reportParams(query, options),
      })
      .pipe(map((response) => unwrapData(response)));
  }

  export(
    kind: 'sales' | 'purchases' | 'inventory' | 'profit',
    query: PeriodQuery,
    options: { search: string; scope: DocumentScope; sort: string },
    format: 'csv' | 'xlsx' | 'pdf' = 'csv',
  ): Observable<Blob> {
    return this.http.get(`${environment.apiUrl}/reports/${kind}/export`, {
      params: exportParams(query, options).set('format', format),
      responseType: 'blob',
    });
  }

  inventoryPrint(query: PeriodQuery & { search: string; sort: string }): Observable<ReportPageResult<ReportRow>> {
    return this.http
      .get<ApiResponse<ReportPageResult<ReportRow>>>(`${environment.apiUrl}/reports/inventory/print`, {
        params: exportParams(query, { search: query.search, scope: 'ALL', sort: query.sort }),
      })
      .pipe(map((response) => unwrapData(response)));
  }
}

function exportParams(
  query: PeriodQuery,
  options: { search: string; scope: DocumentScope; sort: string },
): HttpParams {
  let params = periodParams(query).set('sort', options.sort);
  if (options.search.trim()) {
    params = params.set('search', options.search.trim());
  }
  if (options.scope && options.scope !== 'ALL') {
    params = params.set('scope', options.scope);
  }
  return params;
}

function periodParams(query: PeriodQuery): HttpParams {
  let params = new HttpParams().set('preset', query.preset);
  if (query.preset === 'CUSTOM' && query.from && query.to) {
    params = params.set('from', query.from).set('to', query.to);
  }
  return params;
}

function reportParams(
  query: PeriodQuery,
  options: { search: string; scope: DocumentScope; page: number; size: number; sort: string },
): HttpParams {
  let params = periodParams(query)
    .set('page', String(options.page))
    .set('size', String(options.size))
    .set('sort', options.sort);
  if (options.search.trim()) {
    params = params.set('search', options.search.trim());
  }
  if (options.scope) {
    params = params.set('scope', options.scope);
  }
  return params;
}

import { HttpClient, HttpContext } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { environment } from '../../../environments/environment';
import { SKIP_ERROR_NOTIFICATION } from '../../core/http/http-context';
import { unwrapData } from '../../core/http/unwrap';
import { ApiResponse } from '../../core/models/api-response.model';
import { BusinessSettings, DataResetResult, Letterhead, ResetTableOption } from './settings.model';

@Injectable({ providedIn: 'root' })
export class SettingsApi {
  private readonly http = inject(HttpClient);

  current(): Observable<BusinessSettings> {
    return this.http
      .get<ApiResponse<BusinessSettings>>(`${environment.apiUrl}/settings`, { context: quiet() })
      .pipe(map((response) => unwrapData(response)));
  }

  save(body: BusinessSettings): Observable<BusinessSettings> {
    return this.http
      .put<ApiResponse<BusinessSettings>>(`${environment.apiUrl}/settings`, body, { context: quiet() })
      .pipe(map((response) => unwrapData(response)));
  }

  resetTables(): Observable<ResetTableOption[]> {
    return this.http
      .get<ApiResponse<ResetTableOption[]>>(`${environment.apiUrl}/settings/data-reset`, { context: quiet() })
      .pipe(map((response) => unwrapData(response)));
  }

  resetData(tables: string[]): Observable<DataResetResult> {
    return this.http
      .post<ApiResponse<DataResetResult>>(`${environment.apiUrl}/settings/data-reset`, { tables }, { context: quiet() })
      .pipe(map((response) => unwrapData(response)));
  }

  letterhead(): Observable<Letterhead> {
    return this.http
      .get<ApiResponse<Letterhead>>(`${environment.apiUrl}/system/letterhead`)
      .pipe(map((response) => unwrapData(response)));
  }
}

function quiet(): HttpContext {
  return new HttpContext().set(SKIP_ERROR_NOTIFICATION, true);
}

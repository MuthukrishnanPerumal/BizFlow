import { HttpClient, HttpContext } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { environment } from '../../../environments/environment';
import { SKIP_ERROR_NOTIFICATION, SKIP_LOADING_INDICATOR } from '../http/http-context';
import { ApiResponse } from '../models/api-response.model';
import { CommercialDefaults, SystemHealth, SystemInfo } from '../models/system.model';

@Injectable({ providedIn: 'root' })
export class SystemApi {
  private readonly http = inject(HttpClient);

  info(): Observable<SystemInfo> {
    return this.get<SystemInfo>('/system/info');
  }

  health(): Observable<SystemHealth> {
    return this.get<SystemHealth>('/system/health');
  }

  preferences(): Observable<CommercialDefaults> {
    return this.get<CommercialDefaults>('/system/preferences');
  }

  private get<T>(path: string): Observable<T> {
    const context = new HttpContext()
      .set(SKIP_ERROR_NOTIFICATION, true)
      .set(SKIP_LOADING_INDICATOR, true);
    return this.http
      .get<ApiResponse<T>>(`${environment.apiUrl}${path}`, { context })
      .pipe(map((response) => this.unwrap(response)));
  }

  private unwrap<T>(response: ApiResponse<T>): T {
    if (!response.success || response.data === undefined) {
      throw new Error(response.message || 'Request could not be completed');
    }
    return response.data;
  }
}

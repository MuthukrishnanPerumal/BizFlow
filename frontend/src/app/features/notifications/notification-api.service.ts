import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { environment } from '../../../environments/environment';
import { SKIP_ERROR_NOTIFICATION } from '../../core/http/http-context';
import { unwrapData } from '../../core/http/unwrap';
import { ApiResponse } from '../../core/models/api-response.model';
import { AppNotification, NotificationPage, NotificationSummary } from './notification.model';

@Injectable({ providedIn: 'root' })
export class NotificationApi {
  private readonly http = inject(HttpClient);

  list(kind: string, unreadOnly: boolean, page: number): Observable<NotificationPage> {
    let params = new HttpParams().set('page', page).set('size', 20).set('unreadOnly', unreadOnly);
    if (kind) {
      params = params.set('kind', kind);
    }
    return this.http
      .get<ApiResponse<NotificationPage>>(`${environment.apiUrl}/notifications`, { params, context: quiet() })
      .pipe(map((response) => unwrapData(response)));
  }

  summary(): Observable<NotificationSummary> {
    return this.http
      .get<ApiResponse<NotificationSummary>>(`${environment.apiUrl}/notifications/summary`, { context: quiet() })
      .pipe(map((response) => unwrapData(response)));
  }

  markRead(id: number): Observable<AppNotification> {
    return this.http
      .post<ApiResponse<AppNotification>>(`${environment.apiUrl}/notifications/${id}/read`, {}, { context: quiet() })
      .pipe(map((response) => unwrapData(response)));
  }

  markAllRead(): Observable<NotificationSummary> {
    return this.http
      .post<ApiResponse<NotificationSummary>>(`${environment.apiUrl}/notifications/read-all`, {}, { context: quiet() })
      .pipe(map((response) => unwrapData(response)));
  }
}

function quiet(): HttpContext {
  return new HttpContext().set(SKIP_ERROR_NOTIFICATION, true);
}

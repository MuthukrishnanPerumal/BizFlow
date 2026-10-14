import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { environment } from '../../../environments/environment';
import { SKIP_ERROR_NOTIFICATION } from '../../core/http/http-context';
import { unwrapData } from '../../core/http/unwrap';
import { ApiResponse } from '../../core/models/api-response.model';
import { LedgerEntry, MasterPage, PartyRecord, RecordStatus } from '../../core/models/master.model';
import { PartyResource } from './party.config';

export interface PartyWrite {
  supplierCode?: string;
  supplierName?: string;
  customerCode?: string;
  customerName?: string;
  companyName: string | null;
  contactPerson?: string | null;
  phone: string | null;
  alternatePhone: string | null;
  email: string | null;
  taxNumber: string | null;
  address: string | null;
  country: string | null;
  state: string | null;
  city: string | null;
  pincode: string | null;
  openingBalance: string;
  creditLimit: string;
  paymentTerms: string | null;
  status: RecordStatus;
  version?: number;
}

@Injectable({ providedIn: 'root' })
export class PartyApi {
  private readonly http = inject(HttpClient);

  list(
    resource: PartyResource,
    search: string,
    status: string,
    page: number,
    sort: string,
    size = 20,
  ): Observable<MasterPage<PartyRecord>> {
    let params = new HttpParams().set('page', page).set('size', size).set('sort', sort);
    if (search) {
      params = params.set('search', search);
    }
    if (status) {
      params = params.set('status', status);
    }
    return this.http
      .get<ApiResponse<MasterPage<PartyRecord>>>(`${environment.apiUrl}/${resource}`, { params, context: quiet() })
      .pipe(map((response) => unwrapData(response)));
  }

  get(resource: PartyResource, id: number): Observable<PartyRecord> {
    return this.http
      .get<ApiResponse<PartyRecord>>(`${environment.apiUrl}/${resource}/${id}`, { context: quiet() })
      .pipe(map((response) => unwrapData(response)));
  }

  ledger(resource: PartyResource, id: number, page: number): Observable<MasterPage<LedgerEntry>> {
    const params = new HttpParams().set('page', page).set('size', 20).set('sort', 'entryDate,desc');
    return this.http
      .get<ApiResponse<MasterPage<LedgerEntry>>>(`${environment.apiUrl}/${resource}/${id}/ledger`, { params, context: quiet() })
      .pipe(map((response) => unwrapData(response)));
  }

  create(resource: PartyResource, body: PartyWrite): Observable<PartyRecord> {
    return this.http
      .post<ApiResponse<PartyRecord>>(`${environment.apiUrl}/${resource}`, body, { context: quiet() })
      .pipe(map((response) => unwrapData(response)));
  }

  update(resource: PartyResource, id: number, body: PartyWrite): Observable<PartyRecord> {
    return this.http
      .put<ApiResponse<PartyRecord>>(`${environment.apiUrl}/${resource}/${id}`, body, { context: quiet() })
      .pipe(map((response) => unwrapData(response)));
  }

  delete(resource: PartyResource, id: number): Observable<void> {
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

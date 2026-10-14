import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { environment } from '../../../environments/environment';
import { SKIP_ERROR_NOTIFICATION } from '../../core/http/http-context';
import { unwrapData } from '../../core/http/unwrap';
import { ApiResponse } from '../../core/models/api-response.model';
import { PurchaseDetail, PurchasePage, PurchaseWrite, ReturnDetail, ReturnPage } from './purchase.model';

@Injectable({ providedIn: 'root' })
export class PurchaseApi {
  private readonly http = inject(HttpClient);

  list(query: PurchaseQuery): Observable<PurchasePage> {
    return this.http
      .get<ApiResponse<PurchasePage>>(`${environment.apiUrl}/purchases`, { params: pageParams(query), context: quiet() })
      .pipe(map((response) => unwrapData(response)));
  }

  get(id: number): Observable<PurchaseDetail> {
    return this.http
      .get<ApiResponse<PurchaseDetail>>(`${environment.apiUrl}/purchases/${id}`, { context: quiet() })
      .pipe(map((response) => unwrapData(response)));
  }

  create(body: PurchaseWrite): Observable<PurchaseDetail> {
    return this.http
      .post<ApiResponse<PurchaseDetail>>(`${environment.apiUrl}/purchases`, body, { context: quiet() })
      .pipe(map((response) => unwrapData(response)));
  }

  update(id: number, body: PurchaseWrite): Observable<PurchaseDetail> {
    return this.http
      .put<ApiResponse<PurchaseDetail>>(`${environment.apiUrl}/purchases/${id}`, body, { context: quiet() })
      .pipe(map((response) => unwrapData(response)));
  }

  confirm(id: number, version: number): Observable<PurchaseDetail> {
    return this.http
      .post<ApiResponse<PurchaseDetail>>(`${environment.apiUrl}/purchases/${id}/confirm`, { version }, { context: quiet() })
      .pipe(map((response) => unwrapData(response)));
  }

  cancel(id: number, version: number): Observable<PurchaseDetail> {
    return this.http
      .post<ApiResponse<PurchaseDetail>>(`${environment.apiUrl}/purchases/${id}/cancel`, { version }, { context: quiet() })
      .pipe(map((response) => unwrapData(response)));
  }

  returns(query: ReturnQuery): Observable<ReturnPage> {
    return this.http
      .get<ApiResponse<ReturnPage>>(`${environment.apiUrl}/purchase-returns`, { params: pageParams(query), context: quiet() })
      .pipe(map((response) => unwrapData(response)));
  }

  returnDetail(id: number): Observable<ReturnDetail> {
    return this.http
      .get<ApiResponse<ReturnDetail>>(`${environment.apiUrl}/purchase-returns/${id}`, { context: quiet() })
      .pipe(map((response) => unwrapData(response)));
  }

  createReturn(body: ReturnWrite): Observable<ReturnDetail> {
    return this.http
      .post<ApiResponse<ReturnDetail>>(`${environment.apiUrl}/purchase-returns`, body, { context: quiet() })
      .pipe(map((response) => unwrapData(response)));
  }
}

export interface PurchaseQuery {
  search: string;
  status: string;
  supplierId: number | null;
  invoiceNumber: string;
  dateFrom: string;
  dateTo: string;
  page: number;
  sort: string;
}

export interface ReturnQuery {
  search: string;
  supplierId: number | null;
  purchaseId: number | null;
  dateFrom: string;
  dateTo: string;
  page: number;
  sort: string;
}

export interface ReturnWrite {
  purchaseId: number;
  purchaseVersion: number;
  returnDate: string;
  notes: string | null;
  items: Array<{ purchaseItemId: number; quantity: string; freeQuantity: string; serials: string[] }>;
}

function pageParams(query: PurchaseQuery | ReturnQuery): HttpParams {
  let params = new HttpParams().set('page', query.page).set('size', 20).set('sort', query.sort);
  if (query.search) {
    params = params.set('search', query.search);
  }
  if (query.dateFrom) {
    params = params.set('dateFrom', query.dateFrom);
  }
  if (query.dateTo) {
    params = params.set('dateTo', query.dateTo);
  }
  if (query.supplierId) {
    params = params.set('supplierId', query.supplierId);
  }
  if ('status' in query && query.status) {
    params = params.set('status', query.status);
  }
  if ('invoiceNumber' in query && query.invoiceNumber) {
    params = params.set('invoiceNumber', query.invoiceNumber);
  }
  if ('purchaseId' in query && query.purchaseId) {
    params = params.set('purchaseId', query.purchaseId);
  }
  return params;
}

function quiet(): HttpContext {
  return new HttpContext().set(SKIP_ERROR_NOTIFICATION, true);
}

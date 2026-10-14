import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { environment } from '../../../environments/environment';
import { SKIP_ERROR_NOTIFICATION } from '../../core/http/http-context';
import { unwrapData } from '../../core/http/unwrap';
import { ApiResponse } from '../../core/models/api-response.model';
import { ReturnDetail, ReturnPage, ReturnWrite, SaleDetail, SalePage, SaleWrite, StockBucket } from './sale.model';

@Injectable({ providedIn: 'root' })
export class SaleApi {
  private readonly http = inject(HttpClient);

  list(query: SaleQuery): Observable<SalePage> {
    return this.http
      .get<ApiResponse<SalePage>>(`${environment.apiUrl}/sales`, { params: pageParams(query), context: quiet() })
      .pipe(map((response) => unwrapData(response)));
  }

  get(id: number): Observable<SaleDetail> {
    return this.http
      .get<ApiResponse<SaleDetail>>(`${environment.apiUrl}/sales/${id}`, { context: quiet() })
      .pipe(map((response) => unwrapData(response)));
  }

  create(body: SaleWrite): Observable<SaleDetail> {
    return this.http
      .post<ApiResponse<SaleDetail>>(`${environment.apiUrl}/sales`, body, { context: quiet() })
      .pipe(map((response) => unwrapData(response)));
  }

  update(id: number, body: SaleWrite): Observable<SaleDetail> {
    return this.http
      .put<ApiResponse<SaleDetail>>(`${environment.apiUrl}/sales/${id}`, body, { context: quiet() })
      .pipe(map((response) => unwrapData(response)));
  }

  confirm(id: number, version: number): Observable<SaleDetail> {
    return this.http
      .post<ApiResponse<SaleDetail>>(`${environment.apiUrl}/sales/${id}/confirm`, { version }, { context: quiet() })
      .pipe(map((response) => unwrapData(response)));
  }

  cancel(id: number, version: number): Observable<SaleDetail> {
    return this.http
      .post<ApiResponse<SaleDetail>>(`${environment.apiUrl}/sales/${id}/cancel`, { version }, { context: quiet() })
      .pipe(map((response) => unwrapData(response)));
  }

  stock(productId: number): Observable<StockBucket[]> {
    const params = new HttpParams().set('productId', productId);
    return this.http
      .get<ApiResponse<StockBucket[]>>(`${environment.apiUrl}/inventory/balances`, { params, context: quiet() })
      .pipe(map((response) => unwrapData(response)));
  }

  returns(query: ReturnQuery): Observable<ReturnPage> {
    return this.http
      .get<ApiResponse<ReturnPage>>(`${environment.apiUrl}/sales-returns`, { params: pageParams(query), context: quiet() })
      .pipe(map((response) => unwrapData(response)));
  }

  returnDetail(id: number): Observable<ReturnDetail> {
    return this.http
      .get<ApiResponse<ReturnDetail>>(`${environment.apiUrl}/sales-returns/${id}`, { context: quiet() })
      .pipe(map((response) => unwrapData(response)));
  }

  createReturn(body: ReturnWrite): Observable<ReturnDetail> {
    return this.http
      .post<ApiResponse<ReturnDetail>>(`${environment.apiUrl}/sales-returns`, body, { context: quiet() })
      .pipe(map((response) => unwrapData(response)));
  }
}

export interface SaleQuery {
  search: string;
  status: string;
  paymentType: string;
  customerId: number | null;
  invoiceNumber: string;
  dateFrom: string;
  dateTo: string;
  page: number;
  sort: string;
}

export interface ReturnQuery {
  search: string;
  customerId: number | null;
  saleId: number | null;
  dateFrom: string;
  dateTo: string;
  page: number;
  sort: string;
}

function pageParams(query: SaleQuery | ReturnQuery): HttpParams {
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
  if ('status' in query && query.status) {
    params = params.set('status', query.status);
  }
  if ('paymentType' in query && query.paymentType) {
    params = params.set('paymentType', query.paymentType);
  }
  if ('invoiceNumber' in query && query.invoiceNumber) {
    params = params.set('invoiceNumber', query.invoiceNumber);
  }
  if (query.customerId) {
    params = params.set('customerId', query.customerId);
  }
  if ('saleId' in query && query.saleId) {
    params = params.set('saleId', query.saleId);
  }
  return params;
}

function quiet(): HttpContext {
  return new HttpContext().set(SKIP_ERROR_NOTIFICATION, true);
}

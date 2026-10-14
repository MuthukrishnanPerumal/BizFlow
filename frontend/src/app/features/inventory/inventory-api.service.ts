import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { environment } from '../../../environments/environment';
import { SKIP_ERROR_NOTIFICATION } from '../../core/http/http-context';
import { unwrapData } from '../../core/http/unwrap';
import { ApiResponse } from '../../core/models/api-response.model';
import {
  InventoryDashboard,
  LedgerEntry,
  OutOfStockRow,
  Page,
  ProductStock,
  StockAdjustment,
  StockLocation,
  StockPosition,
  StockTransfer,
  ValuationReport,
} from './inventory.model';

@Injectable({ providedIn: 'root' })
export class InventoryApi {
  private readonly http = inject(HttpClient);

  dashboard(locationId: number | null): Observable<InventoryDashboard> {
    return this.http
      .get<ApiResponse<InventoryDashboard>>(`${environment.apiUrl}/inventory/dashboard`, {
        params: locationParams(locationId),
        context: quiet(),
      })
      .pipe(map((response) => unwrapData(response)));
  }

  overview(query: StockQuery): Observable<Page<StockPosition>> {
    return this.page<StockPosition>('/inventory/overview', stockParams(query));
  }

  lowStock(query: StockQuery): Observable<Page<StockPosition>> {
    return this.page<StockPosition>('/inventory/low-stock', stockParams(query));
  }

  outOfStock(query: StockQuery): Observable<Page<OutOfStockRow>> {
    return this.page<OutOfStockRow>('/inventory/out-of-stock', stockParams(query));
  }

  valuation(query: StockQuery): Observable<ValuationReport> {
    return this.http
      .get<ApiResponse<ValuationReport>>(`${environment.apiUrl}/inventory/valuation`, {
        params: stockParams(query),
        context: quiet(),
      })
      .pipe(map((response) => unwrapData(response)));
  }

  ledger(query: LedgerQuery): Observable<Page<LedgerEntry>> {
    let params = pageParams(query.page, query.sort);
    params = set(params, 'search', query.search);
    params = set(params, 'productId', query.productId);
    params = set(params, 'locationId', query.locationId);
    params = set(params, 'transactionType', query.transactionType);
    params = set(params, 'dateFrom', query.dateFrom);
    params = set(params, 'dateTo', query.dateTo);
    return this.page<LedgerEntry>('/inventory/ledger', params);
  }

  product(productId: number, locationId: number | null): Observable<ProductStock> {
    return this.http
      .get<ApiResponse<ProductStock>>(`${environment.apiUrl}/inventory/products/${productId}`, {
        params: locationParams(locationId),
        context: quiet(),
      })
      .pipe(map((response) => unwrapData(response)));
  }

  locations(): Observable<StockLocation[]> {
    return this.http
      .get<ApiResponse<StockLocation[]>>(`${environment.apiUrl}/inventory/locations`, { context: quiet() })
      .pipe(map((response) => unwrapData(response)));
  }

  createLocation(code: string, name: string): Observable<StockLocation> {
    return this.http
      .post<ApiResponse<StockLocation>>(`${environment.apiUrl}/inventory/locations`, { code, name }, { context: quiet() })
      .pipe(map((response) => unwrapData(response)));
  }

  adjustments(query: DocumentQuery & { direction: string }): Observable<Page<StockAdjustment>> {
    let params = pageParams(query.page, query.sort);
    params = set(params, 'search', query.search);
    params = set(params, 'locationId', query.locationId);
    params = set(params, 'direction', query.direction);
    params = set(params, 'dateFrom', query.dateFrom);
    params = set(params, 'dateTo', query.dateTo);
    return this.page<StockAdjustment>('/inventory/adjustments', params);
  }

  createAdjustment(body: AdjustmentWrite): Observable<StockAdjustment> {
    return this.http
      .post<ApiResponse<StockAdjustment>>(`${environment.apiUrl}/inventory/adjustments`, body, { context: quiet() })
      .pipe(map((response) => unwrapData(response)));
  }

  transfers(query: DocumentQuery & { status: string }): Observable<Page<StockTransfer>> {
    let params = pageParams(query.page, query.sort);
    params = set(params, 'search', query.search);
    params = set(params, 'status', query.status);
    params = set(params, 'dateFrom', query.dateFrom);
    params = set(params, 'dateTo', query.dateTo);
    return this.page<StockTransfer>('/inventory/transfers', params);
  }

  transfer(id: number): Observable<StockTransfer> {
    return this.http
      .get<ApiResponse<StockTransfer>>(`${environment.apiUrl}/inventory/transfers/${id}`, { context: quiet() })
      .pipe(map((response) => unwrapData(response)));
  }

  createTransfer(body: TransferWrite): Observable<StockTransfer> {
    return this.http
      .post<ApiResponse<StockTransfer>>(`${environment.apiUrl}/inventory/transfers`, body, { context: quiet() })
      .pipe(map((response) => unwrapData(response)));
  }

  confirmTransfer(id: number, version: number): Observable<StockTransfer> {
    return this.action(id, 'confirm', version);
  }

  completeTransfer(id: number, version: number): Observable<StockTransfer> {
    return this.action(id, 'complete', version);
  }

  cancelTransfer(id: number, version: number): Observable<StockTransfer> {
    return this.action(id, 'cancel', version);
  }

  private action(id: number, action: string, version: number): Observable<StockTransfer> {
    return this.http
      .post<ApiResponse<StockTransfer>>(
        `${environment.apiUrl}/inventory/transfers/${id}/${action}`,
        { version },
        { context: quiet() },
      )
      .pipe(map((response) => unwrapData(response)));
  }

  private page<T>(path: string, params: HttpParams): Observable<Page<T>> {
    return this.http
      .get<ApiResponse<Page<T>>>(`${environment.apiUrl}${path}`, { params, context: quiet() })
      .pipe(map((response) => unwrapData(response)));
  }
}

export interface StockQuery {
  search: string;
  categoryId: number | null;
  locationId: number | null;
  status: string;
  page: number;
  sort: string;
}

export interface LedgerQuery {
  search: string;
  productId: number | null;
  locationId: number | null;
  transactionType: string;
  dateFrom: string;
  dateTo: string;
  page: number;
  sort: string;
}

export interface DocumentQuery {
  search: string;
  locationId: number | null;
  dateFrom: string;
  dateTo: string;
  page: number;
  sort: string;
}

export interface AdjustmentWrite {
  productId: number;
  locationId: number | null;
  batch: string | null;
  expiryDate: string | null;
  direction: string;
  quantity: string;
  unitCost: string | null;
  reason: string;
  referenceNumber: string | null;
  adjustmentDate: string;
  notes: string | null;
  serials: string[] | null;
}

export interface TransferWrite {
  fromLocationId: number;
  toLocationId: number;
  transferDate: string;
  reason: string;
  notes: string | null;
  complete: boolean;
  lines: Array<{
    productId: number;
    batch: string | null;
    expiryDate: string | null;
    quantity: string;
    serials: string[] | null;
  }>;
}

function quiet(): HttpContext {
  return new HttpContext().set(SKIP_ERROR_NOTIFICATION, true);
}

function pageParams(page: number, sort: string): HttpParams {
  return new HttpParams().set('page', page).set('size', 20).set('sort', sort);
}

function locationParams(locationId: number | null): HttpParams {
  return locationId == null ? new HttpParams() : new HttpParams().set('locationId', locationId);
}

function stockParams(query: StockQuery): HttpParams {
  let params = pageParams(query.page, query.sort);
  params = set(params, 'search', query.search);
  params = set(params, 'categoryId', query.categoryId);
  params = set(params, 'locationId', query.locationId);
  params = set(params, 'status', query.status);
  return params;
}

function set(params: HttpParams, key: string, value: string | number | null | undefined): HttpParams {
  if (value === null || value === undefined || value === '') {
    return params;
  }
  return params.set(key, value);
}

import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { environment } from '../../../environments/environment';
import { SKIP_ERROR_NOTIFICATION } from '../../core/http/http-context';
import { unwrapData } from '../../core/http/unwrap';
import { ApiResponse } from '../../core/models/api-response.model';
import { MasterPage, ProductRecord, RecordStatus, StockType } from '../../core/models/master.model';

export interface ProductWrite {
  productCode: string;
  barcode: string | null;
  name: string;
  shortName: string | null;
  description: string | null;
  categoryId: number;
  brandId: number;
  unitId: number;
  taxPercent: string;
  purchasePrice: string;
  sellingPrice: string;
  mrp: string;
  minimumSellingPrice: string;
  reorderLevel: string;
  maximumStockLevel: string | null;
  stockType: StockType;
  batchTracking: boolean;
  serialTracking: boolean;
  expiryTracking: boolean;
  status: RecordStatus;
  version?: number;
}

@Injectable({ providedIn: 'root' })
export class ProductApi {
  private readonly http = inject(HttpClient);

  list(
    search: string,
    status: string,
    stockType: string,
    categoryId: number | null,
    page: number,
    sort: string,
    size = 20,
  ): Observable<MasterPage<ProductRecord>> {
    let params = new HttpParams().set('page', page).set('size', size).set('sort', sort);
    if (search) {
      params = params.set('search', search);
    }
    if (status) {
      params = params.set('status', status);
    }
    if (stockType) {
      params = params.set('stockType', stockType);
    }
    if (categoryId) {
      params = params.set('categoryId', categoryId);
    }
    return this.http
      .get<ApiResponse<MasterPage<ProductRecord>>>(`${environment.apiUrl}/products`, { params, context: quiet() })
      .pipe(map((response) => unwrapData(response)));
  }

  get(id: number): Observable<ProductRecord> {
    return this.http
      .get<ApiResponse<ProductRecord>>(`${environment.apiUrl}/products/${id}`, { context: quiet() })
      .pipe(map((response) => unwrapData(response)));
  }

  lookup(barcode: string): Observable<ProductRecord> {
    const params = new HttpParams().set('barcode', barcode);
    return this.http
      .get<ApiResponse<ProductRecord>>(`${environment.apiUrl}/products/lookup`, { params, context: quiet() })
      .pipe(map((response) => unwrapData(response)));
  }

  create(body: ProductWrite): Observable<ProductRecord> {
    return this.http
      .post<ApiResponse<ProductRecord>>(`${environment.apiUrl}/products`, body, { context: quiet() })
      .pipe(map((response) => unwrapData(response)));
  }

  update(id: number, body: ProductWrite): Observable<ProductRecord> {
    return this.http
      .put<ApiResponse<ProductRecord>>(`${environment.apiUrl}/products/${id}`, body, { context: quiet() })
      .pipe(map((response) => unwrapData(response)));
  }

  delete(id: number): Observable<void> {
    return this.http
      .delete<ApiResponse<unknown>>(`${environment.apiUrl}/products/${id}`, { context: quiet() })
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

import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { environment } from '../../../environments/environment';
import { SKIP_ERROR_NOTIFICATION } from '../../core/http/http-context';
import { unwrapData } from '../../core/http/unwrap';
import { ApiResponse } from '../../core/models/api-response.model';
import {
  ExpenseDetail,
  ExpenseReport,
  ExpenseWrite,
  OpenInvoice,
  PartyKind,
  PayableRow,
  PaymentDetail,
  PaymentReport,
  PaymentWrite,
  PositionReport,
  ReceivableRow,
} from './finance.model';

@Injectable({ providedIn: 'root' })
export class FinanceApi {
  private readonly http = inject(HttpClient);

  payments(party: PartyKind, query: Record<string, string>): Observable<PaymentReport> {
    return this.http
      .get<ApiResponse<PaymentReport>>(this.paymentRoot(party), { params: params(query), context: quiet() })
      .pipe(map((response) => unwrapData(response)));
  }

  payment(party: PartyKind, id: number): Observable<PaymentDetail> {
    return this.http
      .get<ApiResponse<PaymentDetail>>(`${this.paymentRoot(party)}/${id}`, { context: quiet() })
      .pipe(map((response) => unwrapData(response)));
  }

  openInvoices(party: PartyKind, partyId: number): Observable<OpenInvoice[]> {
    const key = party === 'customer' ? 'customerId' : 'supplierId';
    return this.http
      .get<ApiResponse<OpenInvoice[]>>(`${this.paymentRoot(party)}/open-invoices`, {
        params: new HttpParams().set(key, partyId),
        context: quiet(),
      })
      .pipe(map((response) => unwrapData(response)));
  }

  createPayment(party: PartyKind, body: PaymentWrite): Observable<PaymentDetail> {
    return this.http
      .post<ApiResponse<PaymentDetail>>(this.paymentRoot(party), body, { context: quiet() })
      .pipe(map((response) => unwrapData(response)));
  }

  updatePayment(party: PartyKind, id: number, body: PaymentWrite): Observable<PaymentDetail> {
    return this.http
      .put<ApiResponse<PaymentDetail>>(`${this.paymentRoot(party)}/${id}`, body, { context: quiet() })
      .pipe(map((response) => unwrapData(response)));
  }

  cancelPayment(party: PartyKind, id: number, version: number): Observable<PaymentDetail> {
    return this.http
      .post<ApiResponse<PaymentDetail>>(`${this.paymentRoot(party)}/${id}/cancel`, { version }, { context: quiet() })
      .pipe(map((response) => unwrapData(response)));
  }

  expenses(query: Record<string, string>): Observable<ExpenseReport> {
    return this.http
      .get<ApiResponse<ExpenseReport>>(`${environment.apiUrl}/expenses`, { params: params(query), context: quiet() })
      .pipe(map((response) => unwrapData(response)));
  }

  expense(id: number): Observable<ExpenseDetail> {
    return this.http
      .get<ApiResponse<ExpenseDetail>>(`${environment.apiUrl}/expenses/${id}`, { context: quiet() })
      .pipe(map((response) => unwrapData(response)));
  }

  createExpense(body: ExpenseWrite): Observable<ExpenseDetail> {
    return this.http
      .post<ApiResponse<ExpenseDetail>>(`${environment.apiUrl}/expenses`, body, { context: quiet() })
      .pipe(map((response) => unwrapData(response)));
  }

  updateExpense(id: number, body: ExpenseWrite): Observable<ExpenseDetail> {
    return this.http
      .put<ApiResponse<ExpenseDetail>>(`${environment.apiUrl}/expenses/${id}`, body, { context: quiet() })
      .pipe(map((response) => unwrapData(response)));
  }

  cancelExpense(id: number, version: number): Observable<ExpenseDetail> {
    return this.http
      .post<ApiResponse<ExpenseDetail>>(`${environment.apiUrl}/expenses/${id}/cancel`, { version }, { context: quiet() })
      .pipe(map((response) => unwrapData(response)));
  }

  receivables(query: Record<string, string>): Observable<PositionReport<ReceivableRow>> {
    return this.http
      .get<ApiResponse<PositionReport<ReceivableRow>>>(`${environment.apiUrl}/receivables`, {
        params: params(query),
        context: quiet(),
      })
      .pipe(map((response) => unwrapData(response)));
  }

  payables(query: Record<string, string>): Observable<PositionReport<PayableRow>> {
    return this.http
      .get<ApiResponse<PositionReport<PayableRow>>>(`${environment.apiUrl}/payables`, {
        params: params(query),
        context: quiet(),
      })
      .pipe(map((response) => unwrapData(response)));
  }

  private paymentRoot(party: PartyKind): string {
    return `${environment.apiUrl}/${party === 'customer' ? 'customer-payments' : 'supplier-payments'}`;
  }
}

function quiet(): HttpContext {
  return new HttpContext().set(SKIP_ERROR_NOTIFICATION, true);
}

function params(query: Record<string, string>): HttpParams {
  let value = new HttpParams();
  for (const [key, entry] of Object.entries(query)) {
    if (entry !== '') {
      value = value.set(key, entry);
    }
  }
  return value;
}

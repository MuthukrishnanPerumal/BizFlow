import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';

import { NotificationService } from '../../core/services/notification.service';
import { PaymentFormPage } from '../finance/payment-form-page';
import { SystemSnapshot } from '../../core/services/system-snapshot.service';
import { PurchaseReturnFormPage } from '../purchase-returns/purchase-return-form-page';
import { PurchaseFormPage } from '../purchases/purchase-form-page';
import { SaleReturnFormPage } from '../sales-returns/sale-return-form-page';
import { SaleFormPage } from './sale-form-page';

describe('transaction workspaces', () => {
  beforeEach(() => {
    TestBed.resetTestingModule();
  });

  function route(data: Record<string, string> = {}, query: Record<string, string> = {}) {
    return {
      provide: ActivatedRoute,
      useValue: {
        snapshot: {
          data,
          paramMap: { get: () => null },
          queryParamMap: { get: (key: string) => query[key] ?? null },
        },
        data: of(data),
        paramMap: of({ get: () => null }),
      },
    };
  }

  async function setup(extra: unknown[] = []): Promise<void> {
    await TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        { provide: NotificationService, useValue: { success: () => undefined, error: () => undefined } },
        { provide: SystemSnapshot, useValue: { preferences: () => null } },
        route(),
        ...extra,
      ],
    }).compileComponents();
  }

  it('opens a sale as search, cart, customer, payment, and complete', async () => {
    await setup();
    const fixture = TestBed.createComponent(SaleFormPage);
    fixture.detectChanges();
    const text = fixture.nativeElement.textContent as string;

    expect(text).toContain('Product');
    expect(text).toContain('Cart');
    expect(text).toContain('Customer');
    expect(text).toContain('Grand total');
    expect(text).toContain('Payment');
    expect(text).toContain('Complete sale');
    expect(fixture.nativeElement.querySelector('input[placeholder="Scan or type a barcode"]')).toBeTruthy();
  });

  it('opens a purchase in the same order of supplier, items, total, and payment', async () => {
    await setup();
    const fixture = TestBed.createComponent(PurchaseFormPage);
    fixture.detectChanges();
    const text = fixture.nativeElement.textContent as string;

    expect(text).toContain('Supplier');
    expect(text).toContain('Purchase details');
    expect(text).toContain('Items');
    expect(text).toContain('Grand total');
    expect(text).toContain('Payment');
    expect(text).toContain('Post purchase');
  });

  it('guides a sales return from the original invoice', async () => {
    await setup();
    const fixture = TestBed.createComponent(SaleReturnFormPage);
    fixture.detectChanges();
    const text = fixture.nativeElement.textContent as string;

    expect(text).toContain('Original transaction');
    expect(text).toContain('Invoice number');
    expect(text).not.toContain('Eligible items');
  });

  it('asks for the original purchase before a purchase return', async () => {
    await setup();
    const fixture = TestBed.createComponent(PurchaseReturnFormPage);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Open a purchase and choose Return goods.');
  });

  it('keeps payment amount, allocation, and save on one screen', async () => {
    await setup([route({ party: 'customer' })]);
    const fixture = TestBed.createComponent(PaymentFormPage);
    fixture.detectChanges();
    const text = fixture.nativeElement.textContent as string;

    expect(text).toContain('Customer');
    expect(text).toContain('Amount');
    expect(text).toContain('Open invoices');
    expect(text).toContain('Apply to oldest invoices');
    expect(text).toContain('Save payment');
    expect(text).toContain('Advance');
  });
});

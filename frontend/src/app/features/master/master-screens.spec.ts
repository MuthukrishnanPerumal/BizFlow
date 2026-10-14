import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { MasterPage, PartyRecord, ProductRecord } from '../../core/models/master.model';
import { ConfirmationService } from '../../core/services/confirmation.service';
import { NotificationService } from '../../core/services/notification.service';
import { PurchaseApi } from '../purchases/purchase-api.service';
import { ProductApi } from '../products/product-api.service';
import { ProductFormPage } from '../products/product-form-page';
import { ProductListPage } from '../products/product-list-page';
import { SaleApi } from '../sales/sale-api.service';
import { FinanceApi } from '../finance/finance-api.service';
import { CatalogApi } from './catalog-api.service';
import { CatalogListPage } from './catalog-list-page';
import { PartyApi } from './party-api.service';
import { PartyDetailPage } from './party-detail-page';
import { PartyFormPage } from './party-form-page';
import { PartyListPage } from './party-list-page';

const product: ProductRecord = {
  id: 4,
  productCode: 'SKU-1',
  barcode: null,
  name: 'Notebook',
  shortName: null,
  description: null,
  category: { id: 1, code: 'ST', name: 'Stationery' },
  brand: { id: 2, code: 'AV', name: 'Aveline' },
  unit: { id: 3, name: 'Piece', shortName: 'pcs', decimalAllowed: false },
  taxPercent: '0',
  purchasePrice: '10',
  sellingPrice: '15',
  mrp: '18',
  minimumSellingPrice: '12',
  reorderLevel: '5',
  maximumStockLevel: null,
  stockType: 'NONE',
  batchTracking: false,
  serialTracking: false,
  expiryTracking: false,
  status: 'ACTIVE',
  createdAt: '2026-10-01T00:00:00Z',
  updatedAt: '2026-10-01T00:00:00Z',
  version: 1,
};

const supplier: PartyRecord = {
  id: 7,
  supplierCode: 'SUP-1',
  supplierName: 'North Mill',
  companyName: 'North Mill Co',
  contactPerson: 'Asha',
  phone: '9000000000',
  alternatePhone: null,
  email: 'asha@north.example',
  taxNumber: null,
  address: '12 Mill Road',
  country: 'India',
  state: 'Tamil Nadu',
  city: 'Chennai',
  pincode: '600001',
  openingBalance: '0',
  creditLimit: '1000',
  paymentTerms: 'Net 30',
  status: 'ACTIVE',
  createdAt: '2026-10-01T00:00:00Z',
  updatedAt: '2026-10-01T00:00:00Z',
  version: 1,
  account: { debitTotal: '0', creditTotal: '0', balance: '250' },
};

function page<T>(items: T[]): MasterPage<T> {
  return { items, page: 0, size: 20, totalElements: items.length, totalPages: items.length ? 1 : 0 };
}

function route(data: Record<string, string>, id: string | null = null, tab: string | null = null) {
  return {
    provide: ActivatedRoute,
    useValue: {
      snapshot: {
        data,
        paramMap: { get: (key: string) => (key === 'id' ? id : null) },
        queryParamMap: { get: (key: string) => (key === 'tab' ? tab : null) },
      },
    },
  };
}

describe('master data screens', () => {
  const purchases = vi.fn(() => of(page([])));

  beforeEach(() => {
    purchases.mockClear();
  });

  async function setup(extra: unknown[] = []): Promise<void> {
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        { provide: AuthService, useValue: { hasPermission: () => true } },
        { provide: ConfirmationService, useValue: { confirm: () => of(false) } },
        { provide: NotificationService, useValue: { success: () => undefined, error: () => undefined } },
        { provide: CatalogApi, useValue: { list: () => of(page([])), get: () => of(null) } },
        { provide: ProductApi, useValue: { list: () => of(page([product])), get: () => of(product) } },
        { provide: PartyApi, useValue: { list: () => of(page([supplier])), get: () => of(supplier), ledger: () => of(page([])) } },
        { provide: PurchaseApi, useValue: { list: purchases } },
        { provide: SaleApi, useValue: { list: () => of(page([])) } },
        { provide: FinanceApi, useValue: { payments: () => of(page([])), openInvoices: () => of([]) } },
        ...extra,
      ],
    }).compileComponents();
  }

  it('lists products with search, hidden extra filters, status, and a primary action plus More', async () => {
    await setup();
    const fixture = TestBed.createComponent(ProductListPage);
    fixture.detectChanges();
    const text = fixture.nativeElement.textContent as string;
    const advanced = fixture.nativeElement.querySelector('.advanced') as HTMLElement;

    expect(text).toContain('Products');
    expect(text).toContain('New product');
    expect(fixture.nativeElement.querySelector('app-search-bar')).toBeTruthy();
    expect(text).toContain('Status');
    expect(text).toContain('More filters');
    expect(advanced.hidden).toBe(true);
    expect(text).toContain('Notebook');
    expect(text).toContain('Open');
    expect(text).toContain('More');

    fixture.componentInstance.onStatus('INACTIVE');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Inactive');
    expect(fixture.nativeElement.textContent).toContain('Clear all');

    fixture.componentInstance.clearFilters();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).not.toContain('Clear all');
  });

  it('uses the same empty-state action on a catalog list', async () => {
    await setup([{ provide: CatalogApi, useValue: { list: () => of(page([])), get: () => of(null) } }, route({ resource: 'categories' })]);
    const fixture = TestBed.createComponent(CatalogListPage);
    fixture.detectChanges();
    const text = fixture.nativeElement.textContent as string;

    expect(text).toContain('Categories');
    expect(text).toContain('No categories yet');
    expect(text).toContain('New category');
    expect(text).toContain('Status');
    expect(fixture.nativeElement.querySelector('.advanced')).toBeNull();
  });

  it('opens a supplier from the shared party list', async () => {
    await setup([route({ resource: 'suppliers' })]);
    const fixture = TestBed.createComponent(PartyListPage);
    fixture.detectChanges();
    const text = fixture.nativeElement.textContent as string;

    expect(text).toContain('Suppliers');
    expect(text).toContain('New supplier');
    expect(text).toContain('North Mill');
    expect(text).toContain('Open');
    expect(text).toContain('More');
  });

  it('groups a new product and keeps advanced settings closed', async () => {
    await setup([route({}, null)]);
    const fixture = TestBed.createComponent(ProductFormPage);
    fixture.detectChanges();
    const text = fixture.nativeElement.textContent as string;

    expect(text).toContain('Basic information');
    expect(text).toContain('Classification');
    expect(text).toContain('Pricing');
    expect(text).toContain('Inventory');
    expect(text).toContain('Advanced settings');
    expect(text).toContain('Show advanced settings');
    expect(fixture.nativeElement.querySelector('input[formcontrolname="barcode"]')).toBeNull();
    expect(fixture.componentInstance.form.controls.sellingPrice.value).toBe('0');
    expect(fixture.componentInstance.form.controls.stockType.value).toBe('NONE');
    expect(fixture.componentInstance.form.controls.status.value).toBe('ACTIVE');

    const show = [...fixture.nativeElement.querySelectorAll('button')].find(
      (button: HTMLButtonElement) => button.textContent?.includes('Show advanced settings'),
    ) as HTMLButtonElement;
    show.click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('input[formcontrolname="barcode"]')).toBeTruthy();
  });

  it('sections a new supplier and keeps the existing defaults', async () => {
    await setup([route({ resource: 'suppliers' })]);
    const fixture = TestBed.createComponent(PartyFormPage);
    fixture.detectChanges();
    const text = fixture.nativeElement.textContent as string;

    expect(text).toContain('Basic information');
    expect(text).toContain('Contact');
    expect(text).toContain('Address');
    expect(text).toContain('Financial information');
    expect(text).toContain('Status');
    expect(text).toContain('Cancel');
    expect(fixture.componentInstance.form.controls.country.value).toBe('India');
    expect(fixture.componentInstance.form.controls.openingBalance.value).toBe('0');
    expect(fixture.componentInstance.form.controls.status.value).toBe('ACTIVE');
  });

  it('shows supplier detail tabs and loads purchases on demand', async () => {
    await setup([route({ resource: 'suppliers' }, '7')]);
    const fixture = TestBed.createComponent(PartyDetailPage);
    fixture.detectChanges();
    const text = fixture.nativeElement.textContent as string;

    expect(text).toContain('North Mill');
    expect(text).toContain('Overview');
    expect(text).toContain('Purchases');
    expect(text).toContain('Payments');
    expect(text).toContain('Outstanding');
    expect(text).toContain('Activity');
    expect(text).toContain('Payable balance');
    expect(purchases).not.toHaveBeenCalled();

    clickTab(fixture, 'Purchases');
    expect(purchases).toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('No purchases yet');
  });
});

function clickTab(fixture: ComponentFixture<unknown>, label: string): void {
  const tab = [...fixture.nativeElement.querySelectorAll('[role="tab"]')].find(
    (button: HTMLButtonElement) => button.textContent?.trim() === label,
  ) as HTMLButtonElement;
  tab.click();
  fixture.detectChanges();
}

import { PageResult } from './identity.model';

export type RecordStatus = 'ACTIVE' | 'INACTIVE';
export type StockType = 'NONE' | 'BATCH' | 'SERIAL' | 'PICS';

export interface CatalogRecord {
  id: number;
  code?: string;
  name: string;
  shortName?: string;
  description?: string | null;
  decimalAllowed?: boolean;
  status: RecordStatus;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface MasterRef {
  id: number;
  code: string;
  name: string;
  decimalAllowed?: boolean;
}

export interface ProductRecord {
  id: number;
  productCode: string;
  barcode?: string | null;
  name: string;
  shortName?: string | null;
  description?: string | null;
  category: MasterRef;
  brand: MasterRef;
  unit: { id: number; name: string; shortName: string; decimalAllowed: boolean };
  taxPercent: string | number;
  purchasePrice: string | number;
  sellingPrice: string | number;
  mrp: string | number;
  minimumSellingPrice: string | number;
  reorderLevel: string | number;
  maximumStockLevel?: string | number | null;
  stockType: StockType;
  batchTracking: boolean;
  serialTracking: boolean;
  expiryTracking: boolean;
  status: RecordStatus;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface AccountSummary {
  debitTotal: string | number;
  creditTotal: string | number;
  balance: string | number;
}

export interface PartyRecord {
  id: number;
  supplierCode?: string;
  supplierName?: string;
  customerCode?: string;
  customerName?: string;
  companyName?: string | null;
  contactPerson?: string | null;
  phone?: string | null;
  alternatePhone?: string | null;
  email?: string | null;
  taxNumber?: string | null;
  address?: string | null;
  country?: string | null;
  state?: string | null;
  city?: string | null;
  pincode?: string | null;
  openingBalance: string | number;
  creditLimit: string | number;
  paymentTerms?: string | null;
  status: RecordStatus;
  createdAt: string;
  updatedAt: string;
  version: number;
  account?: AccountSummary | null;
}

export interface LedgerEntry {
  id: number;
  entryType: string;
  referenceType?: string | null;
  referenceId?: string | null;
  entryDate: string;
  debitAmount: string | number;
  creditAmount: string | number;
  narration?: string | null;
  createdAt: string;
}

export type MasterPage<T> = PageResult<T>;

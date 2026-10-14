export interface BusinessSettings {
  companyName: string;
  companyAddress: string;
  phone: string;
  email: string;
  taxName: string;
  taxNumber: string;
  currencyCode: string;
  invoicePrefix: string;
  purchasePrefix: string;
  returnPrefix: string;
  purchaseReturnPrefix: string;
  roundingRule: string;
  allowNegativeStock: boolean;
  defaultPaymentMethod: string;
  dateFormat: string;
  decimalPrecision: number;
  version: number;
}

export interface ResetTableOption {
  key: string;
  label: string;
  group: string;
  table: string;
  rowCount: number;
  requires: string[];
}

export interface DataResetResult {
  deletedRows: number;
  tables: { key: string; label: string; deletedRows: number }[];
}

export interface Letterhead {
  companyName: string;
  companyAddress: string;
  phone: string;
  email: string;
  taxName: string;
  taxNumber: string;
  currencyCode: string;
  dateFormat: string;
  decimalPrecision: number;
}

export const CURRENCIES = ['INR', 'USD', 'EUR', 'GBP', 'AED', 'SGD', 'AUD', 'CAD'] as const;
export const ROUNDING_RULES = [
  { id: 'NONE', label: 'No extra rounding' },
  { id: 'NEAREST_UNIT', label: 'Nearest whole unit' },
  { id: 'NEAREST_TENTH', label: 'Nearest tenth' },
] as const;
export const DATE_FORMATS = [
  { id: 'medium', label: 'Locale medium date' },
  { id: 'dd MMM yyyy', label: '04 Oct 2026' },
  { id: 'dd/MM/yyyy', label: '04/10/2026' },
  { id: 'MM/dd/yyyy', label: '10/04/2026' },
  { id: 'yyyy-MM-dd', label: '2026-10-04' },
] as const;
export const PAYMENT_DEFAULTS = ['CASH', 'CARD', 'UPI', 'BANK_TRANSFER', 'OTHER'] as const;

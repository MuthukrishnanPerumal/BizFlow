import { PageResult } from '../../core/models/identity.model';

export type SalePaymentType = 'CASH' | 'CARD' | 'UPI' | 'BANK_TRANSFER' | 'CREDIT' | 'MIXED';
export type TenderMethod = 'CASH' | 'CARD' | 'UPI' | 'BANK_TRANSFER';
export type RefundMethod = TenderMethod | 'CREDIT';
export type SaleStatus = 'DRAFT' | 'CONFIRMED' | 'CANCELLED' | 'COMPLETED' | 'PARTIALLY_RETURNED' | 'RETURNED';

export interface SaleSummary {
  id: number;
  invoiceNumber: string;
  invoiceDate: string;
  customerId: number;
  customerCode: string;
  customerName: string;
  paymentType: SalePaymentType;
  status: SaleStatus;
  grandTotal: string | number;
  paidAmount: string | number;
  balanceAmount: string | number;
  version: number;
}

export interface SaleLine {
  id: number;
  lineNumber: number;
  productId: number;
  productCode: string;
  productName: string;
  unitName: string;
  batchTracking: boolean;
  serialTracking: boolean;
  expiryTracking: boolean;
  decimalAllowed: boolean;
  barcode?: string | null;
  batch?: string | null;
  expiryDate?: string | null;
  quantity: string | number;
  sellingPrice: string | number;
  discount: string | number;
  tax: string | number;
  taxAmount: string | number;
  total: string | number;
  returnedQuantity: string | number;
  remainingQuantity: string | number;
  serials: string[];
}

export interface SaleTender {
  id: number;
  lineNumber: number;
  method: TenderMethod;
  amount: string | number;
  referenceNumber?: string | null;
}

export interface SaleDetail {
  id: number;
  invoiceNumber: string;
  customer: {
    id: number;
    customerCode: string;
    customerName: string;
    companyName?: string | null;
    phone?: string | null;
    email?: string | null;
    taxNumber?: string | null;
    address?: string | null;
    city?: string | null;
    state?: string | null;
    balance: string | number;
  };
  invoiceDate: string;
  paymentType: SalePaymentType;
  referenceNumber?: string | null;
  notes?: string | null;
  status: SaleStatus;
  subtotal: string | number;
  itemDiscount: string | number;
  orderDiscount: string | number;
  totalDiscount: string | number;
  tax: string | number;
  additionalCharges: string | number;
  roundOff: string | number;
  grandTotal: string | number;
  paidAmount: string | number;
  balanceAmount: string | number;
  returnedValue: string | number;
  refundedAmount: string | number;
  editable: boolean;
  cancellable: boolean;
  returnable: boolean;
  items: SaleLine[];
  tenders: SaleTender[];
  payments: Array<{
    id: number;
    paymentNumber: string;
    paymentDate: string;
    paymentMethod: string;
    amount: string | number;
    status: string;
    referenceNumber?: string | null;
  }>;
  stockImpact: Array<{
    id: number;
    date: string;
    productId: number;
    productName: string;
    transactionType: string;
    referenceType: string;
    referenceId: string;
    batch?: string | null;
    expiryDate?: string | null;
    quantityIn: string | number;
    quantityOut: string | number;
    runningBalance: string | number;
    unitCost: string | number;
    totalValue: string | number;
    user?: string | null;
    remarks?: string | null;
  }>;
  returns: Array<{
    id: number;
    returnNumber: string;
    returnDate: string;
    status: string;
    reason: string;
    refundMethod: RefundMethod;
    merchandiseTotal: string | number;
    discountReversal: string | number;
    taxReversal: string | number;
    grandTotal: string | number;
    notes?: string | null;
    items: ReturnLine[];
  }>;
  audits: Array<{
    id: number;
    eventType: string;
    action: string;
    actor: string;
    outcome: string;
    detail?: string | null;
    createdAt: string;
  }>;
  createdAt: string;
  createdBy: string;
  version: number;
}

export interface ReturnLine {
  id: number;
  saleItemId: number;
  productName: string;
  quantity: string | number;
  sellingPrice: string | number;
  discount: string | number;
  taxAmount: string | number;
  total: string | number;
  serials: string[];
}

export interface ReturnSummary {
  id: number;
  returnNumber: string;
  returnDate: string;
  saleId: number;
  invoiceNumber: string;
  customerId: number;
  customerName: string;
  refundMethod: RefundMethod;
  grandTotal: string | number;
  status: string;
}

export interface ReturnDetail {
  id: number;
  returnNumber: string;
  saleId: number;
  invoiceNumber: string;
  customer: SaleDetail['customer'];
  returnDate: string;
  reason: string;
  refundMethod: RefundMethod;
  notes?: string | null;
  status: string;
  merchandiseTotal: string | number;
  discountReversal: string | number;
  taxReversal: string | number;
  grandTotal: string | number;
  items: ReturnLine[];
  stockImpact: SaleDetail['stockImpact'];
  createdAt: string;
  createdBy: string;
  version: number;
}

export type SalePage = PageResult<SaleSummary>;
export type ReturnPage = PageResult<ReturnSummary>;

export interface SaleWrite {
  customerId: number;
  invoiceDate: string;
  paymentType: SalePaymentType;
  referenceNumber: string | null;
  notes: string | null;
  orderDiscount: string;
  additionalCharges: string;
  roundOff: string;
  paidAmount: string;
  confirm: boolean;
  version?: number;
  items: Array<{
    productId: number;
    barcode: string | null;
    batch: string | null;
    expiryDate: string | null;
    quantity: string;
    sellingPrice: string;
    discount: string;
    tax: string;
    serials: string[];
  }>;
  tenders: Array<{ method: TenderMethod; amount: string; referenceNumber: string | null }> | null;
}

export interface ReturnWrite {
  saleId: number;
  saleVersion: number;
  returnDate: string;
  reason: string;
  refundMethod: RefundMethod;
  notes: string | null;
  items: Array<{ saleItemId: number; quantity: string; serials: string[] }>;
}

export interface StockBucket {
  productId: number;
  batch?: string | null;
  expiryDate?: string | null;
  quantityOnHand: string | number;
  unitCost: string | number;
  locationId?: number;
  defaultLocation?: boolean;
  reservedQuantity?: string | number;
  availableQuantity?: string | number;
}

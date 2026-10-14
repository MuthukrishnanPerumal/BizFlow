import { PageResult } from '../../core/models/identity.model';

export type PaymentMethod = 'CASH' | 'CREDIT' | 'BANK' | 'UPI' | 'CHEQUE' | 'CARD';
export type PurchaseStatus = 'DRAFT' | 'CONFIRMED' | 'CANCELLED' | 'COMPLETED' | 'PARTIALLY_RETURNED' | 'RETURNED';

export interface PurchaseSummary {
  id: number;
  purchaseNumber: string;
  invoiceNumber: string;
  purchaseDate: string;
  supplierId: number;
  supplierCode: string;
  supplierName: string;
  paymentType: PaymentMethod;
  status: PurchaseStatus;
  grandTotal: string | number;
  paidAmount: string | number;
  balanceAmount: string | number;
  version: number;
}

export interface PurchaseLine {
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
  freeQuantity: string | number;
  purchasePrice: string | number;
  discount: string | number;
  tax: string | number;
  taxAmount: string | number;
  total: string | number;
  returnedQuantity: string | number;
  returnedFreeQuantity: string | number;
  remainingQuantity: string | number;
  remainingFreeQuantity: string | number;
  serials: string[];
}

export interface PurchaseDetail {
  id: number;
  purchaseNumber: string;
  supplier: {
    id: number;
    supplierCode: string;
    supplierName: string;
    companyName?: string | null;
    phone?: string | null;
    email?: string | null;
    taxNumber?: string | null;
    address?: string | null;
    city?: string | null;
    state?: string | null;
    balance: string | number;
  };
  invoiceNumber: string;
  purchaseDate: string;
  paymentType: PaymentMethod;
  referenceNumber?: string | null;
  notes?: string | null;
  status: PurchaseStatus;
  subtotal: string | number;
  itemDiscount: string | number;
  billDiscount: string | number;
  totalDiscount: string | number;
  tax: string | number;
  additionalCharges: string | number;
  roundOff: string | number;
  grandTotal: string | number;
  paidAmount: string | number;
  balanceAmount: string | number;
  returnedValue: string | number;
  editable: boolean;
  cancellable: boolean;
  returnable: boolean;
  items: PurchaseLine[];
  payments: Array<{
    id: number;
    paymentNumber: string;
    paymentDate: string;
    paymentMethod: PaymentMethod;
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
    merchandiseTotal: string | number;
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
  purchaseItemId: number;
  productName: string;
  quantity: string | number;
  freeQuantity: string | number;
  purchasePrice: string | number;
  discount: string | number;
  taxAmount: string | number;
  total: string | number;
  serials: string[];
}

export interface ReturnSummary {
  id: number;
  returnNumber: string;
  returnDate: string;
  purchaseId: number;
  purchaseNumber: string;
  invoiceNumber: string;
  supplierId: number;
  supplierName: string;
  grandTotal: string | number;
  status: string;
}

export interface ReturnDetail {
  id: number;
  returnNumber: string;
  purchaseId: number;
  purchaseNumber: string;
  invoiceNumber: string;
  supplier: PurchaseDetail['supplier'];
  returnDate: string;
  notes?: string | null;
  status: string;
  merchandiseTotal: string | number;
  grandTotal: string | number;
  items: ReturnLine[];
  stockImpact: PurchaseDetail['stockImpact'];
  createdAt: string;
  createdBy: string;
  version: number;
}

export interface PurchaseWriteLine {
  productId: number;
  barcode: string | null;
  batch: string | null;
  expiryDate: string | null;
  quantity: string;
  freeQuantity: string;
  purchasePrice: string;
  discount: string;
  tax: string;
  serials: string[];
}

export interface PurchaseWrite {
  supplierId: number;
  invoiceNumber: string;
  purchaseDate: string;
  paymentType: PaymentMethod;
  referenceNumber: string | null;
  notes: string | null;
  billDiscount: string;
  additionalCharges: string;
  roundOff: string;
  paidAmount: string;
  confirm: boolean;
  version?: number;
  items: PurchaseWriteLine[];
}

export type PurchasePage = PageResult<PurchaseSummary>;
export type ReturnPage = PageResult<ReturnSummary>;

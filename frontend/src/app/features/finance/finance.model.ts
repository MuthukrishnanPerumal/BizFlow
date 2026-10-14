export type PaymentMethod = 'CASH' | 'CARD' | 'UPI' | 'BANK_TRANSFER' | 'OTHER';
export type PaymentStatus = 'POSTED' | 'CANCELLED';
export type ExpenseCategory =
  | 'RENT'
  | 'UTILITIES'
  | 'SALARY'
  | 'TRANSPORT'
  | 'OFFICE'
  | 'MAINTENANCE'
  | 'MARKETING'
  | 'PROFESSIONAL'
  | 'TAX'
  | 'OTHER';
export type ExpenseStatus = 'POSTED' | 'CANCELLED';
export type PositionStatus = 'OPEN' | 'OVERDUE' | 'SETTLED' | 'CREDIT';
export type PartyKind = 'customer' | 'supplier';

export interface AllocationView {
  invoiceId: number;
  documentType: string;
  documentNumber: string;
  amount: string | number;
  status: string;
}

export interface JournalLineView {
  lineNumber: number;
  accountCode: string;
  accountName: string;
  debit: string | number;
  credit: string | number;
}

export interface AuditView {
  eventType: string;
  actor: string;
  action: string;
  outcome: string;
  detail: string | null;
  createdAt: string;
}

export interface PaymentSummary {
  id: number;
  paymentNumber: string;
  paymentDate: string;
  partyId: number;
  partyCode: string;
  partyName: string;
  paymentMethod: PaymentMethod;
  amount: string | number;
  allocatedAmount: string | number;
  unallocatedAmount: string | number;
  status: PaymentStatus;
  referenceNumber: string | null;
  sourceType: string;
  editable: boolean;
}

export interface PaymentDetail extends PaymentSummary {
  notes: string | null;
  sourceId: string;
  partyOutstanding: string | number;
  journalStatus: string | null;
  allocations: AllocationView[];
  journal: JournalLineView[];
  audits: AuditView[];
  version: number;
}

export interface PaymentReport {
  items: PaymentSummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  postedTotal: string | number;
  advanceTotal: string | number;
  postedCount: number;
}

export interface OpenInvoice {
  invoiceId: number;
  documentNumber: string;
  documentDate: string;
  grandTotal: string | number;
  paidAmount: string | number;
  openBalance: string | number;
  dueDate: string | null;
  overdue: boolean;
  dueDateSupported: boolean;
}

export interface AllocationWrite {
  invoiceId: number;
  amount: string;
}

export interface PaymentWrite {
  customerId?: number;
  supplierId?: number;
  paymentDate: string;
  amount: string;
  paymentMethod: PaymentMethod;
  referenceNumber: string | null;
  notes: string | null;
  allocations: AllocationWrite[];
  version?: number;
}

export interface ExpenseSummary {
  id: number;
  expenseNumber: string;
  expenseDate: string;
  category: ExpenseCategory;
  description: string;
  amount: string | number;
  paymentMethod: PaymentMethod;
  status: ExpenseStatus;
  referenceNumber: string | null;
}

export interface ExpenseDetail extends ExpenseSummary {
  notes: string | null;
  journalStatus: string | null;
  journal: JournalLineView[];
  audits: AuditView[];
  version: number;
}

export interface ExpenseReport {
  items: ExpenseSummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  postedTotal: string | number;
}

export interface ExpenseWrite {
  expenseDate: string;
  category: ExpenseCategory;
  description: string;
  amount: string;
  paymentMethod: PaymentMethod;
  referenceNumber: string | null;
  notes: string | null;
  version?: number;
}

export interface ReceivableRow {
  customerId: number;
  customerCode: string;
  customerName: string;
  paymentTerms: string | null;
  totalSales: string | number;
  totalReturns: string | number;
  totalPayments: string | number;
  outstanding: string | number;
  overdueSupported: boolean;
  overdueAmount: string | number | null;
  status: PositionStatus;
}

export interface PayableRow {
  supplierId: number;
  supplierCode: string;
  supplierName: string;
  paymentTerms: string | null;
  totalPurchases: string | number;
  totalReturns: string | number;
  totalPayments: string | number;
  outstanding: string | number;
  overdueSupported: boolean;
  overdueAmount: string | number | null;
  status: PositionStatus;
}

export interface PositionReport<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  totalOutstanding: string | number;
  totalOverdue: string | number;
  totalSales?: string | number;
  totalPurchases?: string | number;
  totalPayments: string | number;
  overdueCustomers?: number;
  overdueSuppliers?: number;
}

export const PAYMENT_METHODS: PaymentMethod[] = ['CASH', 'CARD', 'UPI', 'BANK_TRANSFER', 'OTHER'];
export const EXPENSE_CATEGORIES: ExpenseCategory[] = [
  'RENT',
  'UTILITIES',
  'SALARY',
  'TRANSPORT',
  'OFFICE',
  'MAINTENANCE',
  'MARKETING',
  'PROFESSIONAL',
  'TAX',
  'OTHER',
];

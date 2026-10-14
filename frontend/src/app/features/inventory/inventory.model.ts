export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface StockLocation {
  id: number;
  code: string;
  name: string;
  defaultLocation: boolean;
  status: string;
}

export interface StockPosition {
  productId: number;
  productCode: string;
  barcode?: string | null;
  productName: string;
  categoryName: string;
  currentStock: string | number;
  reservedStock: string | number;
  availableStock: string | number;
  averageCost: string | number;
  sellingPrice: string | number;
  stockValue: string | number;
  reorderLevel: string | number;
  maximumStockLevel?: string | number | null;
  status: string;
  shortageQuantity: string | number;
}

export interface OutOfStockRow {
  productId: number;
  productCode: string;
  barcode?: string | null;
  productName: string;
  categoryName: string;
  currentStock: string | number;
  reorderLevel: string | number;
  shortageQuantity: string | number;
  lastPurchase?: string | null;
  lastSale?: string | null;
  status: string;
}

export interface LedgerEntry {
  id: number;
  date: string;
  productId: number;
  productCode: string;
  productName: string;
  transactionType: string;
  referenceNumber: string;
  quantityIn: string | number;
  quantityOut: string | number;
  runningBalance: string | number;
  unitCost: string | number;
  totalValue: string | number;
  user: string;
  remarks?: string | null;
  locationId: number;
  locationCode: string;
  locationName: string;
  batch?: string | null;
}

export interface MovementMix {
  transactionType: string;
  quantityIn: string | number;
  quantityOut: string | number;
  movementCount: number;
}

export interface InventoryDashboard {
  valuationMethod: string;
  allowNegativeStock: boolean;
  skuCount: number;
  unitsOnHand: string | number;
  unitsReserved: string | number;
  unitsAvailable: string | number;
  inventoryValue: string | number;
  inStock: number;
  lowStock: number;
  outOfStock: number;
  overStock: number;
  healthPercent: string | number;
  lowStockAlerts: StockPosition[];
  outOfStockAlerts: OutOfStockRow[];
  recentMovements: LedgerEntry[];
  movement: MovementMix[];
}

export interface BalanceBucket {
  productId: number;
  productCode: string;
  productName: string;
  batch?: string | null;
  expiryDate?: string | null;
  quantityOnHand: string | number;
  unitCost: string | number;
  locationId: number;
  locationCode: string;
  locationName: string;
  defaultLocation: boolean;
  reservedQuantity: string | number;
  availableQuantity: string | number;
}

export interface ProductStock {
  position: StockPosition;
  buckets: BalanceBucket[];
  movements: LedgerEntry[];
  lastPurchase?: string | null;
  lastSale?: string | null;
}

export interface ValuationSummary {
  method: string;
  skuCount: number;
  quantity: string | number;
  averageValue: string | number;
  fifoValue: string | number;
  officialValue: string | number;
}

export interface ValuationRow {
  productId: number;
  productCode: string;
  productName: string;
  categoryName: string;
  quantity: string | number;
  averageCost: string | number;
  averageValue: string | number;
  fifoValue: string | number;
  officialValue: string | number;
}

export interface ValuationReport {
  summary: ValuationSummary;
  items: ValuationRow[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface StockAdjustment {
  id: number;
  adjustmentNumber: string;
  adjustmentDate: string;
  direction: string;
  reason: string;
  quantity: string | number;
  unitCost: string | number;
  totalValue: string | number;
  referenceNumber?: string | null;
  notes?: string | null;
  batch?: string | null;
  expiryDate?: string | null;
  productId: number;
  productCode: string;
  productName: string;
  locationId: number;
  locationCode: string;
  locationName: string;
  user: string;
  version: number;
}

export interface TransferLine {
  id: number;
  lineNumber: number;
  productId: number;
  productCode: string;
  productName: string;
  batch?: string | null;
  expiryDate?: string | null;
  quantity: string | number;
  unitCost: string | number;
  totalValue: string | number;
  serials: string[];
}

export interface StockTransfer {
  id: number;
  transferNumber: string;
  transferDate: string;
  fromLocationId: number;
  fromLocationCode: string;
  fromLocationName: string;
  toLocationId: number;
  toLocationCode: string;
  toLocationName: string;
  reason: string;
  notes?: string | null;
  status: string;
  user: string;
  version: number;
  lines: TransferLine[];
}

export const ADJUSTMENT_REASONS = [
  'OPENING_STOCK',
  'DAMAGE',
  'THEFT',
  'COUNT_CORRECTION',
  'WRITE_OFF',
  'FOUND',
  'OTHER',
] as const;

export const LEDGER_TYPES = [
  'OPENING_STOCK',
  'PURCHASE',
  'PURCHASE_RETURN',
  'PURCHASE_REVERSAL',
  'SALE',
  'SALES_RETURN',
  'SALE_REVERSAL',
  'STOCK_ADJUSTMENT',
  'STOCK_TRANSFER_IN',
  'STOCK_TRANSFER_OUT',
] as const;

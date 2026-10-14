import { StockType } from '../../core/models/master.model';

export interface ProductRuleInput {
  purchasePrice: string;
  sellingPrice: string;
  mrp: string;
  minimumSellingPrice: string;
  reorderLevel: string;
  maximumStockLevel: string;
  stockType: StockType;
  batchTracking: boolean;
  serialTracking: boolean;
  expiryTracking: boolean;
  decimalAllowed: boolean;
}

export function productFieldErrors(value: ProductRuleInput): Record<string, string> {
  const errors: Record<string, string> = {};
  const purchase = Number(value.purchasePrice);
  const selling = Number(value.sellingPrice);
  const mrp = Number(value.mrp);
  const minimum = Number(value.minimumSellingPrice);
  const reorder = Number(value.reorderLevel);
  const maximumText = value.maximumStockLevel.trim();
  const maximum = maximumText.length === 0 ? null : Number(maximumText);
  if (Number.isFinite(minimum) && Number.isFinite(selling) && minimum > selling) {
    errors['minimumSellingPrice'] = 'Minimum selling price cannot be above the selling price.';
  }
  if (Number.isFinite(selling) && Number.isFinite(mrp) && selling > mrp) {
    errors['sellingPrice'] = 'Selling price cannot be above MRP.';
  }
  if (Number.isFinite(purchase) && Number.isFinite(mrp) && purchase > mrp) {
    errors['purchasePrice'] = 'Purchase price cannot be above MRP.';
  }
  if (maximum != null && Number.isFinite(maximum) && Number.isFinite(reorder) && maximum < reorder) {
    errors['maximumStockLevel'] = 'Maximum stock cannot be below the reorder level.';
  }
  if (!value.decimalAllowed && Number.isFinite(reorder) && !Number.isInteger(reorder)) {
    errors['reorderLevel'] = 'Reorder level must be a whole number for this unit.';
  }
  if (!value.decimalAllowed && maximum != null && Number.isFinite(maximum) && !Number.isInteger(maximum)) {
    errors['maximumStockLevel'] = 'Maximum stock must be a whole number for this unit.';
  }
  const tracking = trackingMessage(value);
  if (tracking) {
    errors['stockType'] = tracking;
  }
  return errors;
}

function trackingMessage(value: ProductRuleInput): string | null {
  if (value.stockType === 'NONE' && (value.batchTracking || value.serialTracking || value.expiryTracking)) {
    return 'Products without stock tracking cannot use batch, serial, or expiry tracking.';
  }
  if (value.stockType === 'BATCH' && (!value.batchTracking || value.serialTracking)) {
    return 'Batch stock requires batch tracking and cannot use serial tracking.';
  }
  if (value.stockType === 'SERIAL' && (!value.serialTracking || value.batchTracking)) {
    return 'Serial stock requires serial tracking and cannot use batch tracking.';
  }
  if (value.stockType === 'PICS' && (value.batchTracking || value.serialTracking || value.expiryTracking)) {
    return 'Piece stock does not use batch, serial, or expiry tracking.';
  }
  return null;
}

export function trackingFor(stockType: StockType): { batchTracking: boolean; serialTracking: boolean; expiryTracking: boolean } {
  if (stockType === 'BATCH') {
    return { batchTracking: true, serialTracking: false, expiryTracking: false };
  }
  if (stockType === 'SERIAL') {
    return { batchTracking: false, serialTracking: true, expiryTracking: false };
  }
  return { batchTracking: false, serialTracking: false, expiryTracking: false };
}

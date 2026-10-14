export interface LinePreview {
  gross: number;
  discount: number;
  tax: number;
  total: number;
}

export interface DocumentPreview {
  subtotal: number;
  itemDiscount: number;
  totalDiscount: number;
  tax: number;
  additionalCharges: number;
  roundOff: number;
  grandTotal: number;
  paidAmount: number;
  balanceAmount: number;
  lines: LinePreview[];
}

export function amount(value: string | number | null | undefined): number {
  const parsed = typeof value === 'number' ? value : Number(String(value ?? '').trim());
  if (!Number.isFinite(parsed)) {
    return 0;
  }
  return Math.round(parsed * 10000) / 10000;
}

export function moneyText(value: number): string {
  return (Math.round(value * 10000) / 10000).toFixed(4);
}

export function previewDocument(
  lines: Array<{ quantity: string; freeQuantity: string; purchasePrice: string; discount: string; tax: string }>,
  billDiscount: string,
  additionalCharges: string,
  roundOff: string,
  paidAmount: string,
): DocumentPreview {
  const priced = lines.map((line) => previewLine(line));
  const subtotal = sum(priced.map((line) => line.gross));
  const itemDiscount = sum(priced.map((line) => line.discount));
  const tax = sum(priced.map((line) => line.tax));
  const bill = Math.max(0, amount(billDiscount));
  const charges = Math.max(0, amount(additionalCharges));
  const round = amount(roundOff);
  const paid = Math.max(0, amount(paidAmount));
  const grand = round4(subtotal - itemDiscount - bill + tax + charges + round);
  return {
    subtotal,
    itemDiscount,
    totalDiscount: round4(itemDiscount + bill),
    tax,
    additionalCharges: charges,
    roundOff: round,
    grandTotal: grand,
    paidAmount: paid,
    balanceAmount: round4(grand - paid),
    lines: priced,
  };
}

function previewLine(line: { quantity: string; purchasePrice: string; discount: string; tax: string }): LinePreview {
  const gross = round4(Math.max(0, amount(line.quantity)) * Math.max(0, amount(line.purchasePrice)));
  const discount = Math.min(gross, Math.max(0, amount(line.discount)));
  const taxPercent = Math.min(100, Math.max(0, amount(line.tax)));
  const tax = round4(((gross - discount) * taxPercent) / 100);
  return { gross, discount, tax, total: round4(gross - discount + tax) };
}

function sum(values: number[]): number {
  return round4(values.reduce((total, value) => total + value, 0));
}

function round4(value: number): number {
  return Math.round(value * 10000) / 10000;
}

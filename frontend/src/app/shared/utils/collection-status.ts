export type CollectionStatus = 'PAID' | 'PARTIALLY_PAID' | 'UNPAID';

const EPSILON = 0.0001;

/** Display-only reading of amounts the document already stores. */
export function collectionStatus(
  paid: string | number,
  balance: string | number,
  total: string | number,
): CollectionStatus {
  const paidAmount = amount(paid);
  const balanceAmount = amount(balance);
  const totalAmount = amount(total);
  if (balanceAmount > EPSILON && paidAmount > EPSILON) {
    return 'PARTIALLY_PAID';
  }
  if (balanceAmount > EPSILON) {
    return 'UNPAID';
  }
  if (paidAmount > EPSILON || totalAmount <= EPSILON) {
    return 'PAID';
  }
  return 'UNPAID';
}

function amount(value: string | number): number {
  const parsed = typeof value === 'number' ? value : Number(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

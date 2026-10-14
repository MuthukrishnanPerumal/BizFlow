import { formatDate, formatMoney } from '../master/master-format';

export function formatQty(value: string | number | null | undefined): string {
  const amount = typeof value === 'number' ? value : Number(value ?? 0);
  if (Number.isNaN(amount)) {
    return '—';
  }
  return new Intl.NumberFormat('en-IN', { maximumFractionDigits: 4 }).format(amount);
}

export function formatPeriod(from: string | undefined, to: string | undefined): string {
  if (!from || !to) {
    return '';
  }
  if (from === to) {
    return formatDate(from);
  }
  return `${formatDate(from)} – ${formatDate(to)}`;
}

export { formatMoney };

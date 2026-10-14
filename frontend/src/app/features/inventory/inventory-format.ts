export function formatQty(value: string | number | null | undefined): string {
  const amount = typeof value === 'number' ? value : Number(value ?? 0);
  if (Number.isNaN(amount)) {
    return '—';
  }
  return new Intl.NumberFormat('en-IN', { maximumFractionDigits: 4 }).format(amount);
}

export function today(): string {
  const now = new Date();
  const local = new Date(now.getTime() - now.getTimezoneOffset() * 60000);
  return local.toISOString().slice(0, 10);
}

export function label(value: string | null | undefined): string {
  if (!value) {
    return '—';
  }
  return value
    .toLowerCase()
    .split('_')
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ');
}

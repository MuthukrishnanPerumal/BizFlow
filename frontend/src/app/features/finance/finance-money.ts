const SCALE = 10000n;

export function toScaled(value: string | number | null | undefined): bigint {
  if (value === null || value === undefined) {
    return 0n;
  }
  const text = String(value).trim();
  if (!/^-?\d+(\.\d+)?$/.test(text)) {
    return 0n;
  }
  const negative = text.startsWith('-');
  const unsigned = negative ? text.slice(1) : text;
  const [whole, fraction = ''] = unsigned.split('.');
  const digits = `${whole}${fraction.padEnd(4, '0').slice(0, 4)}`;
  const scaled = BigInt(digits || '0');
  return negative ? -scaled : scaled;
}

export function fromScaled(value: bigint): string {
  const negative = value < 0n;
  const absolute = negative ? -value : value;
  const whole = absolute / SCALE;
  const fraction = (absolute % SCALE).toString().padStart(4, '0');
  return `${negative ? '-' : ''}${whole.toString()}.${fraction}`;
}

export function addMoney(left: string | number, right: string | number): string {
  return fromScaled(toScaled(left) + toScaled(right));
}

export function subtractMoney(left: string | number, right: string | number): string {
  return fromScaled(toScaled(left) - toScaled(right));
}

export function compareMoney(left: string | number, right: string | number): number {
  const difference = toScaled(left) - toScaled(right);
  if (difference === 0n) {
    return 0;
  }
  return difference > 0n ? 1 : -1;
}

export function allocateOldest(amount: string, openBalances: string[]): string[] {
  let remaining = toScaled(amount);
  if (remaining < 0n) {
    remaining = 0n;
  }
  return openBalances.map((open) => {
    const room = toScaled(open);
    const take = remaining < room ? remaining : room < 0n ? 0n : room;
    remaining -= take < 0n ? 0n : take;
    return fromScaled(take < 0n ? 0n : take);
  });
}

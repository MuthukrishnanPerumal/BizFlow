import { configureDisplay, dateInputHint, parseUserDate, toIsoDate } from './master-format';

describe('parseUserDate', () => {
  afterEach(() => {
    configureDisplay({ dateFormat: 'medium' });
  });

  it('reads an ISO date without shifting the calendar day', () => {
    const date = parseUserDate('2026-10-05');
    expect(date).not.toBeNull();
    expect(toIsoDate(date!)).toBe('2026-10-05');
  });

  it('reads day-first dates unless the business format is month-first', () => {
    configureDisplay({ dateFormat: 'dd/MM/yyyy' });
    expect(toIsoDate(parseUserDate('05/10/2026')!)).toBe('2026-10-05');
    expect(toIsoDate(parseUserDate('05-10-2026')!)).toBe('2026-10-05');

    configureDisplay({ dateFormat: 'MM/dd/yyyy' });
    expect(toIsoDate(parseUserDate('05/10/2026')!)).toBe('2026-05-10');
    expect(dateInputHint()).toBe('mm/dd/yyyy');
  });

  it('reads a named month and rejects impossible days', () => {
    configureDisplay({ dateFormat: 'dd MMM yyyy' });
    expect(toIsoDate(parseUserDate('05 Oct 2026')!)).toBe('2026-10-05');
    expect(parseUserDate('31 Feb 2026')).toBeNull();
    expect(dateInputHint()).toBe('dd mmm yyyy');
  });
});

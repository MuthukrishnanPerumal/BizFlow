import { TestBed } from '@angular/core/testing';

import { collectionStatus } from '../../utils/collection-status';
import { StatusBadge } from './status-badge';

describe('StatusBadge', () => {
  it('labels payment and stock states in words, with an icon', () => {
    const fixture = TestBed.createComponent(StatusBadge);
    fixture.componentRef.setInput('status', 'PARTIALLY_PAID');
    fixture.detectChanges();

    const badge = fixture.nativeElement.querySelector('.bf-badge') as HTMLElement;
    expect(badge.textContent).toContain('Partially Paid');
    expect(badge.querySelector('mat-icon')?.textContent).toContain('timelapse');
    expect(badge.classList.contains('is-warning')).toBe(true);
    expect(badge.getAttribute('role')).toBe('status');
  });

  it('does not leave raw enum text on the named states', () => {
    const fixture = TestBed.createComponent(StatusBadge);
    for (const [status, label] of [
      ['PAID', 'Paid'],
      ['UNPAID', 'Unpaid'],
      ['COMPLETED', 'Completed'],
      ['PENDING', 'Pending'],
      ['CANCELLED', 'Cancelled'],
      ['ACTIVE', 'Active'],
      ['INACTIVE', 'Inactive'],
      ['LOW_STOCK', 'Low Stock'],
      ['OUT_OF_STOCK', 'Out of Stock'],
    ] as const) {
      fixture.componentRef.setInput('status', status);
      fixture.detectChanges();
      expect(fixture.nativeElement.textContent).toContain(label);
    }
  });
});

describe('collectionStatus', () => {
  it('reads paid, partial, and unpaid from the stored amounts', () => {
    expect(collectionStatus(100, 0, 100)).toBe('PAID');
    expect(collectionStatus(40, 60, 100)).toBe('PARTIALLY_PAID');
    expect(collectionStatus(0, 100, 100)).toBe('UNPAID');
    expect(collectionStatus(0, 0, 0)).toBe('PAID');
  });
});

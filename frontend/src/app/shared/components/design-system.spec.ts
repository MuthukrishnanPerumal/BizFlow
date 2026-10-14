import { TestBed } from '@angular/core/testing';

import { DetailSection } from './detail-section/detail-section';
import { FilterPanel } from './filter-panel/filter-panel';
import { Kpi } from './kpi/kpi';
import { SearchToolbar } from './search-toolbar/search-toolbar';

describe('design system components', () => {
  it('renders a KPI with a scannable value', () => {
    const fixture = TestBed.createComponent(Kpi);
    fixture.componentRef.setInput('label', 'Net profit');
    fixture.componentRef.setInput('value', '12840');
    fixture.componentRef.setInput('hint', 'This month');
    fixture.componentRef.setInput('tone', 'success');
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Net profit');
    expect(text).toContain('12840');
    expect(text).toContain('This month');
    expect(fixture.nativeElement.querySelector('.bf-kpi-value').getAttribute('data-tone')).toBe('success');
  });

  it('renders a detail section title', () => {
    const fixture = TestBed.createComponent(DetailSection);
    fixture.componentRef.setInput('title', 'Supplier');
    fixture.componentRef.setInput('description', 'Bill to');
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Supplier');
    expect(text).toContain('Bill to');
  });

  it('keeps filters open unless a screen asks otherwise', () => {
    const fixture = TestBed.createComponent(FilterPanel);
    fixture.detectChanges();
    const toggle = fixture.nativeElement.querySelector('button') as HTMLButtonElement;
    expect(toggle.getAttribute('aria-expanded')).toBe('true');
    expect(fixture.nativeElement.querySelector('.bf-filter-body').hasAttribute('hidden')).toBe(false);

    toggle.click();
    fixture.detectChanges();
    expect(toggle.getAttribute('aria-expanded')).toBe('false');
    expect(fixture.nativeElement.querySelector('.bf-filter-body').hasAttribute('hidden')).toBe(true);
  });

  it('exposes the search toolbar under its own selector', () => {
    const fixture = TestBed.createComponent(SearchToolbar);
    fixture.componentRef.setInput('placeholder', 'Find a sale');
    fixture.detectChanges();
    const input = fixture.nativeElement.querySelector('input') as HTMLInputElement;
    expect(input.getAttribute('aria-label')).toBe('Find a sale');
  });
});

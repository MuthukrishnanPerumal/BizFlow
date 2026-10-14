import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { AppCurrencyPipe } from '../../pipes/app-currency.pipe';

@Component({
  selector: 'app-currency-display',
  imports: [AppCurrencyPipe],
  template: `<span class="bf-money">{{ amount() | appCurrency: currency() : locale() }}</span>`,
  styleUrl: './currency-display.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CurrencyDisplay {
  readonly amount = input<number | null>(null);
  readonly currency = input('INR');
  readonly locale = input('en-IN');
}

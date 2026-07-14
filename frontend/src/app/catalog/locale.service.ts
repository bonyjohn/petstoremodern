import { Injectable, computed, signal } from '@angular/core';
import { Locale } from './catalog.models';

/** Legacy prices are migrated verbatim, per locale — no FX conversion,
 * so display currency is just a locale-keyed label, not a computed conversion. */
const CURRENCY_CODES: Record<Locale, string> = {
  en_US: 'USD',
  ja_JP: 'JPY',
  zh_CN: 'CNY',
};

/** The shopper's selected catalog locale, shared across the toolbar and every catalog page. */
@Injectable({ providedIn: 'root' })
export class LocaleService {
  readonly locale = signal<Locale>('en_US');

  /** The currency code to format prices in for the currently selected locale. */
  readonly currencyCode = computed(() => CURRENCY_CODES[this.locale()]);

  setLocale(locale: Locale): void {
    this.locale.set(locale);
  }
}

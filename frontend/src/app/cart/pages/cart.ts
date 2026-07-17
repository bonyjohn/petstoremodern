import { CurrencyPipe } from '@angular/common';
import { Component, computed, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { InputNumberModule } from 'primeng/inputnumber';
import { forkJoin } from 'rxjs';

import { CartService } from '../cart.service';
import { CatalogService } from '../../catalog/catalog.service';
import { LocaleService } from '../../catalog/locale.service';
import { ItemResponse } from '../../catalog/catalog.models';

/**
 * Cart page: line items, quantities, subtotal, and checkout. The cart stores
 * only itemId + qty; names, images, and prices are fetched from the catalog in
 * the CURRENT locale and re-fetched when the shopper switches locale — prices
 * are per-locale amounts, not conversions, so a stale snapshot would show the
 * wrong number in the wrong currency.
 */
@Component({
  selector: 'app-cart',
  imports: [RouterLink, FormsModule, ButtonModule, InputNumberModule, CurrencyPipe],
  template: `
    <h2>Your Cart</h2>
    @if (cartService.items().length === 0) {
      <p>Your cart is empty. <a routerLink="/">Keep browsing.</a></p>
    } @else {
      <div class="cart-lines">
        @for (line of cartService.items(); track line.itemId) {
          <div class="cart-line">
            @if (itemFor(line.itemId); as item) {
              <img [src]="'/images/' + item.image" [alt]="item.name" />
              <a [routerLink]="['/item', line.itemId]" class="name">{{ item.name }}</a>
              <span class="unit-price">{{ item.listPrice | currency: currencyCode() }}</span>
            } @else {
              <span></span>
              <a [routerLink]="['/item', line.itemId]" class="name">{{ line.itemId }}</a>
              <span class="unit-price">…</span>
            }
            <p-inputnumber
              [ngModel]="line.qty"
              (ngModelChange)="cartService.updateQty(line.itemId, $event)"
              [min]="1"
              [showButtons]="true"
              buttonLayout="horizontal"
              inputStyleClass="qty-input"
            />
            <p-button icon="pi pi-trash" severity="secondary" text="true" (onClick)="cartService.remove(line.itemId)" />
          </div>
        }
      </div>
      <div class="cart-summary">
        <span class="subtotal-label">Subtotal</span>
        <span class="subtotal-value">{{ subtotal() | currency: currencyCode() }}</span>
      </div>
      <p-button label="Checkout" icon="pi pi-credit-card" routerLink="/checkout" />
    }
  `,
  styles: `
    h2 {
      font-size: 1.5rem;
      margin-bottom: 1.5rem;
    }
    .cart-lines {
      display: flex;
      flex-direction: column;
      gap: 1rem;
      margin-bottom: 1.5rem;
    }
    .cart-line {
      display: grid;
      grid-template-columns: 4rem 1fr 6rem auto auto;
      align-items: center;
      gap: 1rem;
      border-bottom: 1px solid #e5e5e5;
      padding-bottom: 1rem;
    }
    .cart-line img {
      width: 4rem;
      height: 4rem;
      object-fit: contain;
      background: #fafafa;
      border-radius: 8px;
    }
    .name {
      color: inherit;
      text-decoration: none;
      font-weight: 600;
    }
    .unit-price {
      color: #6b6b6b;
    }
    ::ng-deep .qty-input {
      width: 3rem;
    }
    .cart-summary {
      display: flex;
      justify-content: flex-end;
      gap: 1rem;
      font-size: 1.25rem;
      margin-bottom: 1.5rem;
    }
    .subtotal-value {
      font-weight: 700;
    }
  `,
})
export class CartPage {
  protected readonly cartService = inject(CartService);
  private readonly catalogService = inject(CatalogService);
  private readonly localeService = inject(LocaleService);

  protected readonly currencyCode = this.localeService.currencyCode;
  private readonly resolved = signal<Map<string, ItemResponse>>(new Map());
  private lastFetchKey = '';

  protected readonly subtotal = computed(() => {
    const resolved = this.resolved();
    return this.cartService.items().reduce((sum, line) => {
      const item = resolved.get(line.itemId);
      return sum + (item ? item.listPrice * line.qty : 0);
    }, 0);
  });

  constructor() {
    effect(() => {
      const locale = this.localeService.locale();
      const ids = this.cartService.items().map((line) => line.itemId);
      // Qty changes retrigger this effect; only re-fetch when the locale or
      // the set of items actually changed.
      const fetchKey = locale + '|' + ids.join(',');
      if (fetchKey === this.lastFetchKey) {
        return;
      }
      this.lastFetchKey = fetchKey;
      if (ids.length === 0) {
        this.resolved.set(new Map());
        return;
      }
      forkJoin(ids.map((id) => this.catalogService.item(id, locale))).subscribe((items) => {
        this.resolved.set(new Map(items.map((item) => [item.itemId, item])));
      });
    });
  }

  itemFor(itemId: string): ItemResponse | undefined {
    return this.resolved().get(itemId);
  }
}

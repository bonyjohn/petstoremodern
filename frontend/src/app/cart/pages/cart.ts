import { CurrencyPipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { InputNumberModule } from 'primeng/inputnumber';

import { CartService } from '../cart.service';
import { LocaleService } from '../../catalog/locale.service';

/** Cart page: line items, quantities, subtotal, and a checkout stub (Part 4). */
@Component({
  selector: 'app-cart',
  imports: [RouterLink, FormsModule, ButtonModule, InputNumberModule, CurrencyPipe],
  template: `
    <h2>Your Cart</h2>
    @if (cartService.items().length === 0) {
      <p>Your cart is empty. <a routerLink="/">Keep browsing.</a></p>
    } @else {
      <div class="cart-lines">
        @for (item of cartService.items(); track item.itemId) {
          <div class="cart-line">
            <img [src]="'/images/' + item.image" [alt]="item.name" />
            <a [routerLink]="['/item', item.itemId]" class="name">{{ item.name }}</a>
            <span class="unit-price">{{ item.unitPrice | currency: currencyCode() }}</span>
            <p-inputnumber
              [ngModel]="item.qty"
              (ngModelChange)="cartService.updateQty(item.itemId, $event)"
              [min]="1"
              [showButtons]="true"
              buttonLayout="horizontal"
              inputStyleClass="qty-input"
            />
            <p-button icon="pi pi-trash" severity="secondary" text="true" (onClick)="cartService.remove(item.itemId)" />
          </div>
        }
      </div>
      <div class="cart-summary">
        <span class="subtotal-label">Subtotal</span>
        <span class="subtotal-value">{{ cartService.subtotal() | currency: currencyCode() }}</span>
      </div>
      <p-button label="Checkout" icon="pi pi-credit-card" [disabled]="true" />
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
  private readonly localeService = inject(LocaleService);

  protected readonly currencyCode = this.localeService.currencyCode;
}

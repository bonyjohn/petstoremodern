import { CurrencyPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { CheckboxModule } from 'primeng/checkbox';
import { MessageModule } from 'primeng/message';

import { CartService } from '../../cart/cart.service';
import { LocaleService } from '../../catalog/locale.service';
import { CustomerService } from '../../auth/customer.service';
import { OrderService } from '../order.service';
import { OrderContact, OrderCreditCard } from '../order.models';

function emptyContact(): OrderContact {
  return {
    familyName: '',
    givenName: '',
    address: { street: [''], city: '', state: '', zipCode: '', country: '' },
    email: '',
    phone: '',
  };
}

/**
 * Checkout page: billing & shipping pre-filled from the customer's profile
 * (legacy parity), cart summary, Place Order posting itemId+qty only — the
 * server prices the order.
 */
@Component({
  selector: 'app-checkout',
  imports: [FormsModule, ButtonModule, InputTextModule, CheckboxModule, MessageModule, CurrencyPipe],
  template: `
    <h2>Checkout</h2>
    <div class="checkout-layout">
      <div class="forms">
        @for (section of sections(); track section.title) {
          <h3>{{ section.title }}</h3>
          <div class="contact-form">
            <label [for]="section.key + '-givenName'">Given name</label>
            <input [id]="section.key + '-givenName'" pInputText type="text" [(ngModel)]="section.contact.givenName" />
            <label [for]="section.key + '-familyName'">Family name</label>
            <input [id]="section.key + '-familyName'" pInputText type="text" [(ngModel)]="section.contact.familyName" />
            <label [for]="section.key + '-street'">Street</label>
            <input [id]="section.key + '-street'" pInputText type="text" [(ngModel)]="section.contact.address.street[0]" />
            <label [for]="section.key + '-city'">City</label>
            <input [id]="section.key + '-city'" pInputText type="text" [(ngModel)]="section.contact.address.city" />
            <label [for]="section.key + '-state'">State</label>
            <input [id]="section.key + '-state'" pInputText type="text" [(ngModel)]="section.contact.address.state" />
            <label [for]="section.key + '-zipCode'">Zip code</label>
            <input [id]="section.key + '-zipCode'" pInputText type="text" [(ngModel)]="section.contact.address.zipCode" />
            <label [for]="section.key + '-country'">Country</label>
            <input [id]="section.key + '-country'" pInputText type="text" [(ngModel)]="section.contact.address.country" />
            <label [for]="section.key + '-email'">Email</label>
            <input [id]="section.key + '-email'" pInputText type="email" [(ngModel)]="section.contact.email" />
            <label [for]="section.key + '-phone'">Phone</label>
            <input [id]="section.key + '-phone'" pInputText type="text" [(ngModel)]="section.contact.phone" />
          </div>
        }
        <div class="checkbox-row">
          <p-checkbox
            [ngModel]="sameBilling()"
            (ngModelChange)="sameBilling.set($event)"
            [binary]="true"
            inputId="sameBilling"
          />
          <label for="sameBilling">Billing address same as shipping</label>
        </div>

        <h3>Payment</h3>
        <div class="contact-form">
          <label for="cardNumber">Card number</label>
          <input id="cardNumber" pInputText type="text" [(ngModel)]="creditCard.cardNumber" />
          <label for="cardType">Card type</label>
          <input id="cardType" pInputText type="text" [(ngModel)]="creditCard.cardType" />
          <label for="expiryDate">Expiry date</label>
          <input id="expiryDate" pInputText type="text" [(ngModel)]="creditCard.expiryDate" />
        </div>
      </div>

      <div class="summary">
        <h3>Your order</h3>
        @for (item of cartService.items(); track item.itemId) {
          <div class="summary-line">
            <span>{{ item.name }} × {{ item.qty }}</span>
            <span>{{ item.qty * item.unitPrice | currency: currencyCode() }}</span>
          </div>
        }
        <div class="summary-line subtotal">
          <span>Subtotal</span>
          <span>{{ cartService.subtotal() | currency: currencyCode() }}</span>
        </div>
        <p class="note">Prices are confirmed by the store at checkout.</p>
        @if (error()) {
          <p-message severity="error" [text]="error()!" />
        }
        <p-button
          label="Place Order"
          icon="pi pi-check"
          [disabled]="cartService.items().length === 0"
          [loading]="placing()"
          (onClick)="onPlaceOrder()"
        />
      </div>
    </div>
  `,
  styles: `
    h2 {
      font-size: 1.5rem;
      margin-bottom: 1.5rem;
    }
    h3 {
      font-size: 1.1rem;
      margin: 1.25rem 0 0.75rem;
    }
    .checkout-layout {
      display: grid;
      grid-template-columns: 1fr 20rem;
      gap: 3rem;
      align-items: start;
    }
    .contact-form {
      max-width: 28rem;
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
    }
    label {
      font-size: 0.875rem;
      color: #6b6b6b;
    }
    .checkbox-row {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      margin-top: 1rem;
    }
    .summary {
      border: 1px solid #e5e5e5;
      border-radius: 12px;
      padding: 1.25rem;
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
    }
    .summary h3 {
      margin-top: 0;
    }
    .summary-line {
      display: flex;
      justify-content: space-between;
      font-size: 0.95rem;
    }
    .subtotal {
      border-top: 1px solid #e5e5e5;
      padding-top: 0.5rem;
      font-weight: 700;
    }
    .note {
      font-size: 0.8rem;
      color: #6b6b6b;
    }
  `,
})
export class CheckoutPage {
  protected readonly cartService = inject(CartService);
  private readonly localeService = inject(LocaleService);
  private readonly customerService = inject(CustomerService);
  private readonly orderService = inject(OrderService);
  private readonly router = inject(Router);

  protected readonly currencyCode = this.localeService.currencyCode;

  protected readonly shipTo = emptyContact();
  protected readonly billTo = emptyContact();
  protected readonly creditCard: OrderCreditCard = { cardNumber: '', cardType: '', expiryDate: '' };
  protected readonly sameBilling = signal(true);

  protected readonly placing = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly sections = computed(() => {
    const sections = [{ title: 'Ship to', key: 'ship', contact: this.shipTo }];
    if (!this.sameBilling()) {
      sections.push({ title: 'Bill to', key: 'bill', contact: this.billTo });
    }
    return sections;
  });

  constructor() {
    this.customerService.me().subscribe((customer) => {
      const contactInfo = customer.account?.contactInfo;
      if (contactInfo) {
        for (const contact of [this.shipTo, this.billTo]) {
          contact.familyName = contactInfo.familyName ?? '';
          contact.givenName = contactInfo.givenName ?? '';
          contact.email = contactInfo.email ?? '';
          contact.phone = contactInfo.phone ?? '';
          const address = contactInfo.address;
          if (address) {
            contact.address = {
              street: address.street?.length ? [...address.street] : [''],
              city: address.city ?? '',
              state: address.state ?? '',
              zipCode: address.zipCode ?? '',
              country: address.country ?? '',
            };
          }
        }
      }
      const card = customer.account?.creditCard;
      if (card) {
        this.creditCard.cardNumber = card.cardNumber ?? '';
        this.creditCard.cardType = card.cardType ?? '';
        this.creditCard.expiryDate = card.expiryDate ?? '';
      }
    });
  }

  onPlaceOrder(): void {
    this.error.set(null);
    this.placing.set(true);
    this.orderService
      .place({
        locale: this.localeService.locale(),
        lines: this.cartService.items().map((item) => ({ itemId: item.itemId, qty: item.qty })),
        shipTo: this.shipTo,
        billTo: this.sameBilling() ? this.shipTo : this.billTo,
        creditCard: this.creditCard,
      })
      .subscribe({
        next: (order) => {
          this.placing.set(false);
          this.cartService.clear();
          this.router.navigate(['/orders', order.orderId]);
        },
        error: () => {
          this.placing.set(false);
          this.error.set('Could not place the order. Please check the form and try again.');
        },
      });
  }
}

import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { CheckboxModule } from 'primeng/checkbox';
import { MessageModule } from 'primeng/message';

import { CustomerService } from '../customer.service';
import { AccountDto, CustomerUpdateRequest, ProfileDto } from '../auth.models';

const EMPTY_ACCOUNT: AccountDto = {
  contactInfo: {
    familyName: '',
    givenName: '',
    email: '',
    phone: '',
    address: { street: [''], city: '', state: '', zipCode: '', country: '' },
  },
  creditCard: { cardNumber: '', cardType: '', expiryDate: '' },
};

const EMPTY_PROFILE: ProfileDto = {
  preferredLanguage: 'en_US',
  favoriteCategory: '',
  myListPreference: false,
  bannerPreference: false,
};

/**
 * A fresh signup only collects name/email, so `/me` can return null for the
 * address, credit card, or the whole account. Fill any missing pieces with
 * empty defaults so the form always has something to bind to.
 */
function normalizeAccount(account: AccountDto | null): AccountDto {
  const empty = structuredClone(EMPTY_ACCOUNT);
  if (!account) {
    return empty;
  }
  const contactInfo = account.contactInfo ?? empty.contactInfo!;
  const address = contactInfo.address ?? empty.contactInfo!.address!;
  return {
    contactInfo: {
      ...contactInfo,
      address: { ...address, street: address.street?.length ? address.street : [''] },
    },
    creditCard: account.creditCard ?? empty.creditCard,
  };
}

/** The signed-in shopper's own account/profile: view and edit (legacy "My Account"). */
@Component({
  selector: 'app-account',
  imports: [FormsModule, ButtonModule, InputTextModule, CheckboxModule, MessageModule],
  template: `
    <h2>My Account</h2>
    @if (account(); as account) {
      <div class="account-form">
        <label for="familyName">Family name</label>
        <input id="familyName" pInputText type="text" [(ngModel)]="account.contactInfo!.familyName" />
        <label for="givenName">Given name</label>
        <input id="givenName" pInputText type="text" [(ngModel)]="account.contactInfo!.givenName" />
        <label for="email">Email</label>
        <input id="email" pInputText type="email" [(ngModel)]="account.contactInfo!.email" />
        <label for="phone">Phone</label>
        <input id="phone" pInputText type="text" [(ngModel)]="account.contactInfo!.phone" />
        <label for="street">Street</label>
        <input id="street" pInputText type="text" [(ngModel)]="account.contactInfo!.address!.street[0]" />
        <label for="city">City</label>
        <input id="city" pInputText type="text" [(ngModel)]="account.contactInfo!.address!.city" />
        <label for="state">State</label>
        <input id="state" pInputText type="text" [(ngModel)]="account.contactInfo!.address!.state" />
        <label for="zipCode">Zip code</label>
        <input id="zipCode" pInputText type="text" [(ngModel)]="account.contactInfo!.address!.zipCode" />
        <label for="country">Country</label>
        <input id="country" pInputText type="text" [(ngModel)]="account.contactInfo!.address!.country" />

        @if (profile(); as profile) {
          <div class="checkbox-row">
            <p-checkbox [(ngModel)]="profile.myListPreference" [binary]="true" inputId="myList" />
            <label for="myList">Show my list</label>
          </div>
          <div class="checkbox-row">
            <p-checkbox [(ngModel)]="profile.bannerPreference" [binary]="true" inputId="banner" />
            <label for="banner">Show banner</label>
          </div>
        }

        @if (saved()) {
          <p-message severity="success" text="Account updated." />
        }
        <p-button label="Save" (onClick)="onSave()" [loading]="saving()" />
      </div>
    }
  `,
  styles: `
    h2 {
      font-size: 1.5rem;
      margin-bottom: 1.5rem;
    }
    .account-form {
      max-width: 28rem;
      display: flex;
      flex-direction: column;
      gap: 0.75rem;
    }
    label {
      font-size: 0.875rem;
      color: #6b6b6b;
    }
    .checkbox-row {
      display: flex;
      align-items: center;
      gap: 0.5rem;
    }
  `,
})
export class AccountPage {
  private readonly customerService = inject(CustomerService);

  protected readonly account = signal<AccountDto | undefined>(undefined);
  protected readonly profile = signal<ProfileDto | undefined>(undefined);
  protected readonly saving = signal(false);
  protected readonly saved = signal(false);

  constructor() {
    this.customerService.me().subscribe((customer) => {
      this.account.set(normalizeAccount(customer.account));
      this.profile.set(customer.profile ?? structuredClone(EMPTY_PROFILE));
    });
  }

  onSave(): void {
    const account = this.account();
    const profile = this.profile();
    if (!account || !profile) {
      return;
    }
    this.saving.set(true);
    this.saved.set(false);
    const request: CustomerUpdateRequest = { account, profile };
    this.customerService.updateMe(request).subscribe((customer) => {
      this.account.set(normalizeAccount(customer.account));
      this.profile.set(customer.profile ?? structuredClone(EMPTY_PROFILE));
      this.saving.set(false);
      this.saved.set(true);
    });
  }
}

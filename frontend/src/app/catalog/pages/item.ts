import { CurrencyPipe } from '@angular/common';
import { Component, effect, inject, input, signal } from '@angular/core';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';

import { CatalogService } from '../catalog.service';
import { LocaleService } from '../locale.service';
import { ItemResponse } from '../catalog.models';
import { CartService } from '../../cart/cart.service';

/** Item detail page: image, description, price, and an Add-to-Cart button. */
@Component({
  selector: 'app-item',
  imports: [ButtonModule, TagModule, CurrencyPipe],
  template: `
    @if (item(); as item) {
      <div class="item-detail">
        <img [src]="'/images/' + item.image" [alt]="item.name" />
        <div>
          <h2>{{ item.name }}</h2>
          <p class="description">{{ item.description }}</p>
          <p class="price">{{ item.listPrice | currency: currencyCode() }}</p>
          @for (attribute of item.attributes; track attribute) {
            <p-tag [value]="attribute" />
          }
          <div>
            <p-button label="Add to Cart" icon="pi pi-shopping-cart" (onClick)="onAddToCart(item)" />
          </div>
        </div>
      </div>
    }
  `,
  styles: `
    .item-detail {
      display: flex;
      gap: 2rem;
    }
    img {
      width: 16rem;
      height: 16rem;
      object-fit: contain;
      background: #fafafa;
      border-radius: 12px;
    }
    h2 {
      font-size: 1.75rem;
      margin: 0 0 0.5rem;
    }
    .description {
      color: #6b6b6b;
    }
    .price {
      font-size: 1.5rem;
      font-weight: 700;
      color: #1a1a1a;
      margin: 0.75rem 0 1rem;
    }
    .item-detail ::ng-deep .p-tag {
      margin-right: 0.5rem;
    }
    .item-detail > div > div:last-child {
      margin-top: 1.25rem;
    }
  `,
})
export class ItemPage {
  readonly id = input.required<string>();

  private readonly catalogService = inject(CatalogService);
  private readonly localeService = inject(LocaleService);
  private readonly cartService = inject(CartService);

  readonly currencyCode = this.localeService.currencyCode;
  readonly item = signal<ItemResponse | undefined>(undefined);

  constructor() {
    effect(() => {
      this.catalogService.item(this.id(), this.localeService.locale()).subscribe((result) => {
        this.item.set(result);
      });
    });
  }

  onAddToCart(item: ItemResponse): void {
    this.cartService.add({
      itemId: item.itemId,
      productId: item.productId,
      name: item.name,
      image: item.image,
      unitPrice: item.listPrice,
    });
  }
}

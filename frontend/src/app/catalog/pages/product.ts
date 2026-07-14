import { CurrencyPipe } from '@angular/common';
import { Component, effect, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CardModule } from 'primeng/card';
import { TagModule } from 'primeng/tag';

import { CatalogService } from '../catalog.service';
import { LocaleService } from '../locale.service';
import { ItemResponse, ProductResponse } from '../catalog.models';

/** Product page: the product header plus its sellable item variants (legacy product page). */
@Component({
  selector: 'app-product',
  imports: [RouterLink, CardModule, TagModule, CurrencyPipe],
  template: `
    @if (product(); as product) {
      <div class="product-header">
        <img [src]="'/images/' + product.image" [alt]="product.name" />
        <div>
          <h2>{{ product.name }}</h2>
          <p>{{ product.description }}</p>
        </div>
      </div>
    }
    <h2>Choose an item</h2>
    <div class="item-grid">
      @for (item of items(); track item.itemId) {
        <a [routerLink]="['/item', item.itemId]" class="item-tile">
          <p-card [header]="item.name">
            <img [src]="'/images/' + item.image" [alt]="item.name" />
            <p>{{ item.listPrice | currency: currencyCode() }}</p>
            @for (attribute of item.attributes; track attribute) {
              <p-tag [value]="attribute" />
            }
          </p-card>
        </a>
      }
    </div>
  `,
  styles: `
    h2 {
      font-size: 1.5rem;
    }
    .product-header {
      display: flex;
      gap: 2rem;
      margin-bottom: 2.5rem;
    }
    .product-header img {
      width: 12rem;
      height: 12rem;
      object-fit: contain;
      background: #fafafa;
      border-radius: 12px;
    }
    .product-header h2 {
      font-size: 1.75rem;
      margin: 0 0 0.5rem;
    }
    .product-header p {
      color: #6b6b6b;
    }
    .item-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(14rem, 1fr));
      gap: 1.25rem;
    }
    .item-tile {
      text-decoration: none;
      color: inherit;
    }
    ::ng-deep .item-tile .p-card {
      border: 1px solid #e5e5e5;
      box-shadow: none;
      border-radius: 12px;
      transition: box-shadow 0.15s ease, transform 0.15s ease;
    }
    ::ng-deep .item-tile:hover .p-card {
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.06);
      transform: translateY(-2px);
    }
    ::ng-deep .item-tile .p-card-body p {
      font-weight: 600;
    }
    ::ng-deep .item-tile .p-tag {
      margin-right: 0.5rem;
    }
    img {
      display: block;
      width: 100%;
      height: 8rem;
      object-fit: contain;
      background: #fafafa;
    }
  `,
})
export class ProductPage {
  readonly id = input.required<string>();

  private readonly catalogService = inject(CatalogService);
  private readonly localeService = inject(LocaleService);

  readonly currencyCode = this.localeService.currencyCode;
  readonly product = signal<ProductResponse | undefined>(undefined);
  readonly items = signal<ItemResponse[]>([]);

  constructor() {
    effect(() => {
      const id = this.id();
      const locale = this.localeService.locale();
      this.catalogService.product(id, locale).subscribe((result) => {
        this.product.set(result);
      });
      this.catalogService.itemsForProduct(id, locale).subscribe((result) => {
        this.items.set(result);
      });
    });
  }
}

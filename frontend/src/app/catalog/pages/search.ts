import { Component, effect, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CardModule } from 'primeng/card';

import { CatalogService } from '../catalog.service';
import { LocaleService } from '../locale.service';
import { ProductSummaryResponse } from '../catalog.models';

/** Search results page: products matching the toolbar search box (legacy catalog search). */
@Component({
  selector: 'app-search',
  imports: [RouterLink, CardModule],
  template: `
    <h2>Search results for "{{ q() }}"</h2>
    @if (results().length === 0) {
      <p>No products found.</p>
    }
    <div class="product-grid">
      @for (product of results(); track product.productId) {
        <a [routerLink]="['/product', product.productId]" class="product-tile">
          <p-card [header]="product.name">
            <img [src]="'/images/' + product.image" [alt]="product.name" />
            <p>{{ product.description }}</p>
          </p-card>
        </a>
      }
    </div>
  `,
  styles: `
    h2 {
      font-size: 1.5rem;
      margin-bottom: 1.5rem;
    }
    p {
      color: #6b6b6b;
    }
    .product-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(12rem, 1fr));
      gap: 1.25rem;
    }
    .product-tile {
      text-decoration: none;
      color: inherit;
    }
    ::ng-deep .product-tile .p-card {
      border: 1px solid #e5e5e5;
      box-shadow: none;
      border-radius: 12px;
      transition: box-shadow 0.15s ease, transform 0.15s ease;
    }
    ::ng-deep .product-tile:hover .p-card {
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.06);
      transform: translateY(-2px);
    }
    ::ng-deep .product-tile .p-card-body p {
      color: #6b6b6b;
      font-size: 0.9rem;
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
export class SearchPage {
  readonly q = input<string>('');

  private readonly catalogService = inject(CatalogService);
  private readonly localeService = inject(LocaleService);

  readonly results = signal<ProductSummaryResponse[]>([]);

  constructor() {
    effect(() => {
      const query = this.q();
      if (!query) {
        this.results.set([]);
        return;
      }
      this.catalogService.search(query, this.localeService.locale()).subscribe((result) => {
        this.results.set(result);
      });
    });
  }
}

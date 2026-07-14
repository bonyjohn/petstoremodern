import { Component, effect, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CardModule } from 'primeng/card';

import { CatalogService } from '../catalog.service';
import { LocaleService } from '../locale.service';
import { CategoryResponse } from '../catalog.models';

/** Home page: category tiles, mirroring the legacy storefront's category grid. */
@Component({
  selector: 'app-home',
  imports: [RouterLink, CardModule],
  template: `
    <h2>Browse by category</h2>
    <div class="category-grid">
      @for (category of categories(); track category.id) {
        <a [routerLink]="['/category', category.id]" class="category-tile">
          <p-card [header]="category.name">
            <img [src]="'/images/' + category.image" [alt]="category.name" />
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
    .category-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(12rem, 1fr));
      gap: 1.25rem;
    }
    .category-tile {
      text-decoration: none;
      color: inherit;
    }
    ::ng-deep .category-tile .p-card {
      border: 1px solid #e5e5e5;
      box-shadow: none;
      border-radius: 12px;
      transition: box-shadow 0.15s ease, transform 0.15s ease;
    }
    ::ng-deep .category-tile:hover .p-card {
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.06);
      transform: translateY(-2px);
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
export class Home {
  private readonly catalogService = inject(CatalogService);
  private readonly localeService = inject(LocaleService);

  readonly categories = signal<CategoryResponse[]>([]);

  constructor() {
    effect(() => {
      this.catalogService.categories(this.localeService.locale()).subscribe((result) => {
        console.log(1)
        this.categories.set(result);
      });
    });
  }
}

import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { CategoryResponse, ItemResponse, ProductResponse } from './catalog.models';

/** Typed client for `/api/catalog/...`, matching the backend's DTOs. */
@Injectable({ providedIn: 'root' })
export class CatalogService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/catalog';

  categories(locale: string): Observable<CategoryResponse[]> {
    return this.http.get<CategoryResponse[]>(`${this.baseUrl}/categories`, { params: { locale } });
  }

  productsInCategory(categoryId: string, locale: string): Observable<ProductResponse[]> {
    return this.http.get<ProductResponse[]>(
      `${this.baseUrl}/categories/${categoryId}/products`,
      { params: { locale } },
    );
  }

  product(productId: string, locale: string): Observable<ProductResponse> {
    return this.http.get<ProductResponse>(`${this.baseUrl}/products/${productId}`, { params: { locale } });
  }

  itemsForProduct(productId: string, locale: string): Observable<ItemResponse[]> {
    return this.http.get<ItemResponse[]>(`${this.baseUrl}/products/${productId}/items`, { params: { locale } });
  }

  item(itemId: string, locale: string): Observable<ItemResponse> {
    return this.http.get<ItemResponse>(`${this.baseUrl}/items/${itemId}`, { params: { locale } });
  }

  search(query: string, locale: string): Observable<ProductResponse[]> {
    return this.http.get<ProductResponse[]>(`${this.baseUrl}/search`, { params: { q: query, locale } });
  }
}

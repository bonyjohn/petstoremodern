import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { AdminOrderResponse, InventoryItem } from './admin.models';

/**
 * Typed client for the admin APIs. Orders live in core (`/api/admin/orders`);
 * inventory lives in the fulfillment service, reached through the dev proxy's
 * `/fulfillment` route. The auth interceptor attaches the same JWT to both.
 */
@Injectable({ providedIn: 'root' })
export class AdminService {
  private readonly http = inject(HttpClient);

  orders(status?: string): Observable<AdminOrderResponse[]> {
    const params: Record<string, string> = status ? { status } : {};
    return this.http.get<AdminOrderResponse[]>('/api/admin/orders', { params });
  }

  approve(orderId: string): Observable<void> {
    return this.http.post<void>(`/api/admin/orders/${orderId}/approve`, null);
  }

  deny(orderId: string): Observable<void> {
    return this.http.post<void>(`/api/admin/orders/${orderId}/deny`, null);
  }

  inventory(): Observable<InventoryItem[]> {
    return this.http.get<InventoryItem[]>('/fulfillment/api/inventory');
  }

  updateInventory(itemId: string, quantityOnHand: number): Observable<InventoryItem> {
    return this.http.put<InventoryItem>(`/fulfillment/api/inventory/${itemId}`, { quantityOnHand });
  }
}

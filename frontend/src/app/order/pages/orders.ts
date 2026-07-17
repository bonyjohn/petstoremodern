import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TagModule } from 'primeng/tag';

import { OrderService } from '../order.service';
import { OrderResponse } from '../order.models';
import { currencyCodeFor } from '../../catalog/locale.service';

/**
 * My Orders: the signed-in shopper's order history, newest first. Each row's
 * total is formatted in that order's own locale currency (orders carry their
 * locale) — a ja_JP order stays ¥ even while browsing in en_US.
 */
@Component({
  selector: 'app-orders',
  imports: [RouterLink, TagModule, CurrencyPipe, DatePipe],
  template: `
    <h2>My Orders</h2>
    @if (orders().length === 0) {
      <p>No orders yet. <a routerLink="/">Keep browsing.</a></p>
    } @else {
      <div class="order-rows">
        @for (order of orders(); track order.orderId) {
          <div class="order-row">
            <a [routerLink]="['/orders', order.orderId]" class="order-id">Order {{ order.orderId }}</a>
            <span class="order-date">{{ order.orderDate | date: 'medium' }}</span>
            <span class="order-total">{{ order.totalValue | currency: currencyFor(order) }}</span>
            <p-tag [value]="order.status" [severity]="statusSeverity(order.status)" />
          </div>
        }
      </div>
    }
  `,
  styles: `
    h2 {
      font-size: 1.5rem;
      margin-bottom: 1.5rem;
    }
    .order-rows {
      display: flex;
      flex-direction: column;
      gap: 0.75rem;
      max-width: 42rem;
    }
    .order-row {
      display: grid;
      grid-template-columns: 8rem 1fr 8rem auto;
      align-items: center;
      gap: 1rem;
      border-bottom: 1px solid #e5e5e5;
      padding-bottom: 0.75rem;
    }
    .order-id {
      color: inherit;
      font-weight: 600;
      text-decoration: none;
    }
    .order-date {
      color: #6b6b6b;
      font-size: 0.9rem;
    }
    .order-total {
      text-align: right;
      font-weight: 600;
    }
  `,
})
export class OrdersPage {
  private readonly orderService = inject(OrderService);

  protected readonly orders = signal<OrderResponse[]>([]);

  constructor() {
    this.orderService.myOrders().subscribe((orders) => {
      this.orders.set(orders);
    });
  }

  currencyFor(order: OrderResponse): string {
    return currencyCodeFor(order.locale);
  }

  statusSeverity(status: string): 'success' | 'warn' | 'danger' | 'info' {
    switch (status) {
      case 'APPROVED':
      case 'COMPLETED':
        return 'success';
      case 'PENDING':
        return 'warn';
      case 'DENIED':
        return 'danger';
      default:
        return 'info';
    }
  }
}

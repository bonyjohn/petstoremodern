import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, effect, inject, input, signal } from '@angular/core';
import { TagModule } from 'primeng/tag';

import { OrderService } from '../order.service';
import { OrderResponse } from '../order.models';
import { LocaleService } from '../../catalog/locale.service';

/**
 * Order confirmation / detail page: id, status, server-priced lines, total.
 * A small order shows APPROVED here immediately; a large one shows PENDING —
 * the visible face of the auto-approval rule.
 */
@Component({
  selector: 'app-confirmation',
  imports: [TagModule, CurrencyPipe, DatePipe],
  template: `
    @if (order(); as order) {
      <div class="order-header">
        <h2>Order {{ order.orderId }}</h2>
        <p-tag [value]="order.status" [severity]="statusSeverity(order.status)" />
      </div>
      <p class="order-date">Placed {{ order.orderDate | date: 'medium' }}</p>

      <div class="order-lines">
        @for (line of order.lines; track line.lineNo) {
          <div class="order-line">
            <span>{{ line.itemId }}</span>
            <span>× {{ line.qty }}</span>
            <span>{{ line.unitPrice | currency: currencyCode() }}</span>
          </div>
        }
        <div class="order-line total">
          <span>Total</span>
          <span></span>
          <span>{{ order.totalValue | currency: currencyCode() }}</span>
        </div>
      </div>

      @if (order.status === 'PENDING') {
        <p class="pending-note">This order is awaiting approval — we'll email you when it's confirmed.</p>
      }
    }
  `,
  styles: `
    .order-header {
      display: flex;
      align-items: center;
      gap: 1rem;
    }
    h2 {
      font-size: 1.5rem;
      margin: 0;
    }
    .order-date {
      color: #6b6b6b;
      margin: 0.5rem 0 1.5rem;
    }
    .order-lines {
      max-width: 28rem;
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
    }
    .order-line {
      display: grid;
      grid-template-columns: 1fr auto 6rem;
      gap: 1rem;
    }
    .order-line span:last-child {
      text-align: right;
    }
    .total {
      border-top: 1px solid #e5e5e5;
      padding-top: 0.5rem;
      font-weight: 700;
    }
    .pending-note {
      margin-top: 1.5rem;
      color: #6b6b6b;
    }
  `,
})
export class ConfirmationPage {
  readonly id = input.required<string>();

  private readonly orderService = inject(OrderService);
  private readonly localeService = inject(LocaleService);

  protected readonly currencyCode = this.localeService.currencyCode;
  protected readonly order = signal<OrderResponse | undefined>(undefined);

  constructor() {
    effect(() => {
      this.orderService.order(this.id()).subscribe((result) => {
        this.order.set(result);
      });
    });
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

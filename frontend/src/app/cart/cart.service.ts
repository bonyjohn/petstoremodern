import { Injectable, computed, effect, signal } from '@angular/core';

import { CartItem } from './cart.models';

const STORAGE_KEY = 'petstore.cart';

/**
 * Client-side shopping cart, persisted to localStorage. No server calls — the legacy
 * cart lived in the HTTP session; here it's entirely the shopper's browser until
 * checkout posts it to the order service (a later part).
 */
@Injectable({ providedIn: 'root' })
export class CartService {
  readonly items = signal<CartItem[]>(loadFromStorage());

  readonly count = computed(() => this.items().reduce((sum, item) => sum + item.qty, 0));
  readonly subtotal = computed(() => this.items().reduce((sum, item) => sum + item.qty * item.unitPrice, 0));

  constructor() {
    effect(() => {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(this.items()));
    });
  }

  add(item: Omit<CartItem, 'qty'>, qty = 1): void {
    const existing = this.items().find((i) => i.itemId === item.itemId);
    if (existing) {
      this.updateQty(item.itemId, existing.qty + qty);
      return;
    }
    this.items.update((items) => [...items, { ...item, qty }]);
  }

  updateQty(itemId: string, qty: number): void {
    if (qty <= 0) {
      this.remove(itemId);
      return;
    }
    this.items.update((items) => items.map((i) => (i.itemId === itemId ? { ...i, qty } : i)));
  }

  remove(itemId: string): void {
    this.items.update((items) => items.filter((i) => i.itemId !== itemId));
  }

  clear(): void {
    this.items.set([]);
  }
}

function loadFromStorage(): CartItem[] {
  const stored = localStorage.getItem(STORAGE_KEY);
  return stored ? JSON.parse(stored) : [];
}

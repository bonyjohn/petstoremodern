import { Injectable, computed, effect, signal } from '@angular/core';

import { CartItem } from './cart.models';

const STORAGE_KEY = 'petstore.cart';

/**
 * Client-side shopping cart, persisted to localStorage. Holds itemId + qty
 * only; display data (name, image, price) is looked up from the catalog at
 * the current locale by whoever renders the cart. No server calls — the
 * legacy cart lived in the HTTP session; here it's entirely the shopper's
 * browser until checkout posts it to the order service.
 */
@Injectable({ providedIn: 'root' })
export class CartService {
  readonly items = signal<CartItem[]>(loadFromStorage());

  readonly count = computed(() => this.items().reduce((sum, item) => sum + item.qty, 0));

  constructor() {
    effect(() => {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(this.items()));
    });
  }

  add(itemId: string, qty = 1): void {
    const existing = this.items().find((i) => i.itemId === itemId);
    if (existing) {
      this.updateQty(itemId, existing.qty + qty);
      return;
    }
    this.items.update((items) => [...items, { itemId, qty }]);
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
  try {
    const parsed = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '[]');
    if (!Array.isArray(parsed)) {
      return [];
    }
    return parsed
      .filter((entry) => entry && typeof entry.itemId === 'string' && Number.isFinite(entry.qty) && entry.qty > 0)
      .map((entry) => ({ itemId: entry.itemId, qty: Math.floor(entry.qty) }));
  } catch {
    return [];
  }
}

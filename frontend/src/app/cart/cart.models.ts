/**
 * One line in the client-side cart: identity and quantity only. Names, images,
 * and prices are resolved from the catalog at render time in the current
 * locale — a snapshot taken at add-to-cart time would go stale (and show the
 * wrong currency) the moment the shopper switches locale.
 */
export interface CartItem {
  itemId: string;
  qty: number;
}

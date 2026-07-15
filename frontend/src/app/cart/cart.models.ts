/** One line in the client-side cart, denormalized from the item at add-to-cart time. */
export interface CartItem {
  itemId: string;
  productId: string;
  name: string;
  image: string;
  unitPrice: number;
  qty: number;
}

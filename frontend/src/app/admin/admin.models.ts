export interface AdminOrderResponse {
  orderId: string;
  userId: string;
  orderDate: string;
  locale: string;
  totalValue: number;
  status: string;
}

export interface InventoryItem {
  id: string;
  quantityOnHand: number;
}

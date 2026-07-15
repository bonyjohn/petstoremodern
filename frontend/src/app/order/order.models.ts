export interface OrderLineRequest {
  itemId: string;
  qty: number;
}

export interface OrderAddress {
  street: string[];
  city: string;
  state: string;
  zipCode: string;
  country: string;
}

export interface OrderContact {
  familyName: string;
  givenName: string;
  address: OrderAddress;
  email: string;
  phone: string;
}

export interface OrderCreditCard {
  cardNumber: string;
  cardType: string;
  expiryDate: string;
}

export interface PlaceOrderRequest {
  locale: string;
  lines: OrderLineRequest[];
  shipTo: OrderContact;
  billTo: OrderContact;
  creditCard: OrderCreditCard;
}

export interface OrderLineResponse {
  lineNo: number;
  itemId: string;
  productId: string;
  categoryId: string;
  qty: number;
  unitPrice: number;
  qtyShipped: number;
}

export interface StatusChangeResponse {
  status: string;
  at: string;
}

export interface OrderResponse {
  orderId: string;
  status: string;
  orderDate: string;
  locale: string;
  totalValue: number;
  lines: OrderLineResponse[];
  statusHistory: StatusChangeResponse[];
}

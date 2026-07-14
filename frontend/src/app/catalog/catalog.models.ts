export interface CategoryResponse {
  id: string;
  name: string;
  image: string;
}

export interface ProductSummaryResponse {
  productId: string;
  name: string;
  image: string;
  description: string;
}

export interface ProductResponse {
  productId: string;
  categoryId: string;
  name: string;
  image: string;
  description: string;
}

export interface ItemResponse {
  itemId: string;
  productId: string;
  name: string;
  description: string;
  image: string;
  listPrice: number;
  attributes: string[];
}

export const SUPPORTED_LOCALES = ['en_US', 'ja_JP', 'zh_CN'] as const;
export type Locale = (typeof SUPPORTED_LOCALES)[number];

import { Routes } from '@angular/router';

import { Home } from './catalog/pages/home';
import { CategoryPage } from './catalog/pages/category';
import { ProductPage } from './catalog/pages/product';
import { ItemPage } from './catalog/pages/item';
import { SearchPage } from './catalog/pages/search';

export const routes: Routes = [
  { path: '', component: Home },
  { path: 'category/:id', component: CategoryPage },
  { path: 'product/:id', component: ProductPage },
  { path: 'item/:id', component: ItemPage },
  { path: 'search', component: SearchPage },
];

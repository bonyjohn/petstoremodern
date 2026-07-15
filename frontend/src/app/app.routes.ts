import { Routes } from '@angular/router';

import { Home } from './catalog/pages/home';
import { CategoryPage } from './catalog/pages/category';
import { ProductPage } from './catalog/pages/product';
import { ItemPage } from './catalog/pages/item';
import { SearchPage } from './catalog/pages/search';
import { LoginPage } from './auth/pages/login';
import { SignupPage } from './auth/pages/signup';
import { AccountPage } from './auth/pages/account';
import { authGuard } from './auth/auth.guard';
import { CartPage } from './cart/pages/cart';
import { CheckoutPage } from './order/pages/checkout';
import { ConfirmationPage } from './order/pages/confirmation';

export const routes: Routes = [
  { path: '', component: Home },
  { path: 'category/:id', component: CategoryPage },
  { path: 'product/:id', component: ProductPage },
  { path: 'item/:id', component: ItemPage },
  { path: 'search', component: SearchPage },
  { path: 'login', component: LoginPage },
  { path: 'signup', component: SignupPage },
  { path: 'account', component: AccountPage, canActivate: [authGuard] },
  { path: 'cart', component: CartPage },
  { path: 'checkout', component: CheckoutPage, canActivate: [authGuard] },
  { path: 'orders/:id', component: ConfirmationPage, canActivate: [authGuard] },
];

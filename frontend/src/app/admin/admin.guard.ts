import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService } from '../auth/auth.service';

/** Admin pages require a signed-in shopper carrying the ADMIN role. */
export const adminGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  if (authService.isAuthenticated() && authService.roles().includes('ADMIN')) {
    return true;
  }
  return inject(Router).parseUrl('/');
};

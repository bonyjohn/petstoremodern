import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { CustomerResponse, CustomerUpdateRequest } from './auth.models';

/** Typed client for the authenticated shopper's own `/api/customers/me`. */
@Injectable({ providedIn: 'root' })
export class CustomerService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/customers/me';

  me(): Observable<CustomerResponse> {
    return this.http.get<CustomerResponse>(this.baseUrl);
  }

  updateMe(request: CustomerUpdateRequest): Observable<CustomerResponse> {
    return this.http.put<CustomerResponse>(this.baseUrl, request);
  }
}

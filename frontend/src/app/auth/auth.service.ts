import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';

import { LoginRequest, LoginResponse, SignupRequest } from './auth.models';

const STORAGE_KEY = 'petstore.auth';

/** Signed-in shopper's identity and JWT, persisted to localStorage across reloads. */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/auth';

  readonly username = signal<string | null>(null);
  readonly roles = signal<string[]>([]);
  readonly token = signal<string | null>(null);

  readonly isAuthenticated = computed(() => this.token() !== null);

  constructor() {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (stored) {
      this.applySession(JSON.parse(stored));
    }
  }

  login(request: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.baseUrl}/login`, request).pipe(tap((r) => this.setSession(r)));
  }

  signup(request: SignupRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.baseUrl}/signup`, request).pipe(tap((r) => this.setSession(r)));
  }

  logout(): void {
    this.username.set(null);
    this.roles.set([]);
    this.token.set(null);
    localStorage.removeItem(STORAGE_KEY);
  }

  private setSession(response: LoginResponse): void {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(response));
    this.applySession(response);
  }

  private applySession(response: LoginResponse): void {
    this.username.set(response.username);
    this.roles.set(response.roles);
    this.token.set(response.token);
  }
}

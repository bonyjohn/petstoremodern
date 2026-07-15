import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { PasswordModule } from 'primeng/password';
import { MessageModule } from 'primeng/message';

import { AuthService } from '../auth.service';

/** Registration page (legacy new-account flow, trimmed to the essentials). */
@Component({
  selector: 'app-signup',
  imports: [FormsModule, RouterLink, ButtonModule, InputTextModule, PasswordModule, MessageModule],
  template: `
    <div class="auth-form">
      <h2>Create Account</h2>
      <label for="username">Username</label>
      <input id="username" pInputText type="text" [(ngModel)]="username" />
      <label for="password">Password</label>
      <p-password id="password" [(ngModel)]="password" [toggleMask]="true" />
      <label for="givenName">Given name</label>
      <input id="givenName" pInputText type="text" [(ngModel)]="givenName" />
      <label for="familyName">Family name</label>
      <input id="familyName" pInputText type="text" [(ngModel)]="familyName" />
      <label for="email">Email</label>
      <input id="email" pInputText type="email" [(ngModel)]="email" />
      @if (error()) {
        <p-message severity="error" [text]="error()!" />
      }
      <p-button label="Create Account" (onClick)="onSubmit()" [loading]="loading()" />
      <p class="switch">
        Already have an account? <a routerLink="/login">Sign in</a>
      </p>
    </div>
  `,
  styles: `
    .auth-form {
      max-width: 24rem;
      margin: 0 auto;
      display: flex;
      flex-direction: column;
      gap: 0.75rem;
    }
    h2 {
      font-size: 1.5rem;
      margin-bottom: 0.5rem;
    }
    label {
      font-size: 0.875rem;
      color: #6b6b6b;
    }
    .switch {
      margin-top: 0.5rem;
      font-size: 0.9rem;
    }
  `,
})
export class SignupPage {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  protected username = '';
  protected password = '';
  protected givenName = '';
  protected familyName = '';
  protected email = '';
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  onSubmit(): void {
    this.error.set(null);
    this.loading.set(true);
    this.authService
      .signup({
        username: this.username,
        password: this.password,
        givenName: this.givenName,
        familyName: this.familyName,
        email: this.email,
      })
      .subscribe({
        next: () => {
          this.loading.set(false);
          this.router.navigateByUrl('/');
        },
        error: (err: HttpErrorResponse) => {
          this.loading.set(false);
          this.error.set(err.status === 409 ? 'That username is already taken.' : 'Sign up failed.');
        },
      });
  }
}

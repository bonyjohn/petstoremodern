import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterOutlet } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ToolbarModule } from 'primeng/toolbar';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { InputGroupModule } from 'primeng/inputgroup';
import { SelectModule } from 'primeng/select';

import { LocaleService } from './catalog/locale.service';
import { Locale, SUPPORTED_LOCALES } from './catalog/catalog.models';

@Component({
  selector: 'app-root',
  imports: [
    RouterOutlet,
    RouterLink,
    FormsModule,
    ToolbarModule,
    ButtonModule,
    InputTextModule,
    InputGroupModule,
    SelectModule
  ],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App {
  protected readonly localeService = inject(LocaleService);
  private readonly router = inject(Router);

  protected readonly locales: Locale[] = [...SUPPORTED_LOCALES];
  protected searchQuery = '';

  onLocaleChange(locale: Locale): void {
    this.localeService.setLocale(locale);
  }

  onSearch(): void {
    if (this.searchQuery.trim()) {
      this.router.navigate(['/search'], { queryParams: { q: this.searchQuery.trim() } });
    }
  }
}

import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  standalone: true,
  selector: 'app-home',
  template: `
    <h1 class="h1">Volleyball Stats Messenger</h1>
    <p>Send plain-text stat reports to your players. Log in to get started.</p>
    <a routerLink="/send" class="btn">Go to Send Stats</a>
  `,
  imports: [RouterLink],
  styles: [`
    .h1{ margin:1rem 0; }
    .btn{ display:inline-block; margin-top:1rem; padding:0.6rem 1rem; border:1px solid #ddd; border-radius:8px; text-decoration:none; }
    .btn:hover{ background:#f6f6f6; }
  `]
})
export class HomeComponent {}

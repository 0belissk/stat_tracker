import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../auth/auth.service';
import { NgIf } from '@angular/common';

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [RouterLink, NgIf],
  template: `
    <nav class="nav">
      <a routerLink="/" class="brand">VSM</a>
      <a routerLink="/send">Send Stats</a>
      <span class="spacer"></span>
      <button *ngIf="!auth.isLoggedIn()" (click)="auth.login()">Login</button>
      <button *ngIf="auth.isLoggedIn()" (click)="auth.logout()">Logout</button>
    </nav>
  `,
  styles: [
    `
      .nav {
        display: flex;
        gap: 1rem;
        align-items: center;
        padding: 0.75rem 1rem;
        border-bottom: 1px solid #eee;
      }
      .brand {
        font-weight: 600;
      }
      .spacer {
        flex: 1;
      }
      button {
        border: 1px solid #ddd;
        padding: 0.4rem 0.8rem;
        border-radius: 8px;
        background: #fff;
        cursor: pointer;
      }
      button:hover {
        background: #f6f6f6;
      }
    `,
  ],
})
export class NavbarComponent {
  constructor(public auth: AuthService) {}
}

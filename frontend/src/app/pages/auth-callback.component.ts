import { Component } from '@angular/core';

@Component({
  selector: 'app-auth-callback',
  standalone: true,
  template: `
    <h2>Auth Callback</h2>
    <p>You have been redirected back from login. If nothing happens, <a routerLink="/send">continue to Send Stats</a>.</p>
  `
})
export class AuthCallbackComponent {}

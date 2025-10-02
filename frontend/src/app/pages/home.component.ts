import { Component } from '@angular/core';

@Component({
  selector: 'app-home',
  standalone: true,
  template: `
    <h2>Welcome to Volleyball Stats Messenger</h2>
    <p>Use the navbar to navigate. Click "Send Stats" to try the reactive form.</p>
  `
})
export class HomeComponent {}

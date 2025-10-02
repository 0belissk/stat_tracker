import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../auth/auth.service';

@Component({
  standalone: true,
  selector: 'app-auth-callback',
  template: `<p>Signing you in…</p>`,
})
export class AuthCallbackComponent implements OnInit {
  constructor(private router: Router, private auth: AuthService) {}
  async ngOnInit() {
    await new Promise(r => setTimeout(r, 300));
    this.router.navigateByUrl('/send');
  }
}

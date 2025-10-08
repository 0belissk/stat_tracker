import { TestBed } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';

import { AppComponent } from './app.component';
import { NavbarComponent } from './shared/navbar.component';
import { AuthService } from './auth/auth.service';
import { OAuthService } from 'angular-oauth2-oidc';

describe('AppComponent', () => {
  beforeEach(async () => {
    const authServiceStub: Partial<AuthService> = {
      isLoggedIn: () => false,
      login: () => void 0,
      logout: () => void 0,
    };

    const oauthServiceStub: Partial<OAuthService> = {
      configure: () => void 0,
      setupAutomaticSilentRefresh: () => void 0,
      loadDiscoveryDocumentAndTryLogin: () => Promise.resolve(true),
      hasValidAccessToken: () => false,
      hasValidIdToken: () => false,
      getAccessToken: () => '',
      initLoginFlow: () => void 0,
      logOut: () => void 0,
    };

    await TestBed.configureTestingModule({
      imports: [RouterTestingModule, NavbarComponent],
      declarations: [AppComponent],
      providers: [
        { provide: AuthService, useValue: authServiceStub },
        { provide: OAuthService, useValue: oauthServiceStub },
      ],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });
});

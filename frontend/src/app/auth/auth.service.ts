import { Injectable } from '@angular/core';
import { OAuthService, AuthConfig } from 'angular-oauth2-oidc';
import { environment } from '../../environments/environment';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private authConfig: AuthConfig = {
    issuer: `https://cognito-idp.${environment.cognito.region}.amazonaws.com/${environment.cognito.userPoolId}`,
    clientId: environment.cognito.clientId,
    redirectUri: environment.cognito.redirectUri,
    postLogoutRedirectUri: environment.cognito.postLogoutRedirectUri,
    responseType: 'code',
    scope: environment.cognito.scope,
    showDebugInformation: false,
    timeoutFactor: 0.75,
    useSilentRefresh: false,
    disablePKCE: false,
  };

  constructor(private oauth: OAuthService) {}

  async init(): Promise<void> {
    this.oauth.configure(this.authConfig);
    this.oauth.setupAutomaticSilentRefresh();
    await this.oauth.loadDiscoveryDocumentAndTryLogin();
  }

  login(): void { this.oauth.initLoginFlow(); }
  logout(): void { this.oauth.logOut(); }

  get accessToken(): string | null {
    return this.oauth.hasValidAccessToken() ? this.oauth.getAccessToken() : null;
  }
  isLoggedIn(): boolean {
    return this.oauth.hasValidIdToken() || this.oauth.hasValidAccessToken();
  }
}

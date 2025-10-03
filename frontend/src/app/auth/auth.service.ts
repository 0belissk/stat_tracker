import { Injectable } from '@angular/core';
import { OAuthService, AuthConfig } from 'angular-oauth2-oidc';
import { environment } from '../../environments/environment';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private authConfig: AuthConfig = {
    issuer: environment.cognito.issuer,
    clientId: environment.cognito.clientId,
    redirectUri: environment.cognito.redirectUri,
    postLogoutRedirectUri: environment.cognito.postLogoutRedirectUri,
    responseType: environment.cognito.responseType,
    scope: environment.cognito.scope,
    showDebugInformation: false,
    timeoutFactor: 0.75,
    usePkce: environment.cognito.usePkce,
    ...(environment.cognito.silentRefreshRedirectUri
      ? {
          useSilentRefresh: true,
          silentRefreshRedirectUri: environment.cognito.silentRefreshRedirectUri,
        }
      : { useSilentRefresh: false }),
  };

  constructor(private oauth: OAuthService) {}

  async init(): Promise<void> {
    this.oauth.configure(this.authConfig);
    if (environment.cognito.silentRefreshRedirectUri) {
      this.oauth.setupAutomaticSilentRefresh();
    }
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

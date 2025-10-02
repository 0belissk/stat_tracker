import { APP_INITIALIZER, NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { HttpClientModule, HTTP_INTERCEPTORS } from '@angular/common/http';
import { OAuthModule } from 'angular-oauth2-oidc';
import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { AuthService } from './auth/auth.service';
import { JwtInterceptor } from './auth/jwt.interceptor';
import { NavbarComponent } from './shared/navbar.component';

export function initAuth(auth: AuthService) { return () => auth.init(); }

@NgModule({
  declarations: [AppComponent],
  imports: [BrowserModule, HttpClientModule, OAuthModule.forRoot(), AppRoutingModule, NavbarComponent],
  providers: [
    { provide: APP_INITIALIZER, useFactory: initAuth, deps: [AuthService], multi: true },
    { provide: HTTP_INTERCEPTORS, useClass: JwtInterceptor, multi: true }
  ],
  bootstrap: [AppComponent],
})
export class AppModule {}

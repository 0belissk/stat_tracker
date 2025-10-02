import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { HomeComponent } from './pages/home.component';
import { SendStatsComponent } from './pages/send-stats.component';
import { AuthCallbackComponent } from './pages/auth-callback.component';
import { AuthGuard } from './auth/auth.guard';

const routes: Routes = [
  { path: '', component: HomeComponent },
  { path: 'send', component: SendStatsComponent, canActivate: [AuthGuard] },
  { path: 'auth/callback', component: AuthCallbackComponent },
  { path: '**', redirectTo: '' }
];

@NgModule({
  imports: [RouterModule.forRoot(routes, { bindToComponentInputs: true })],
  exports: [RouterModule],
})
export class AppRoutingModule {}

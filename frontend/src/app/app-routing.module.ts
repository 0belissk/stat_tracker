import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

const routes: Routes = [
  { path: '', loadComponent: () => import('./pages/home.component').then(m => m.HomeComponent) },
  { path: 'send', loadComponent: () => import('./pages/send-stats.component').then(m => m.SendStatsComponent) },
  { path: 'auth/callback', loadComponent: () => import('./pages/auth-callback.component').then(m => m.AuthCallbackComponent) },
  { path: '**', redirectTo: '' }
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule {}

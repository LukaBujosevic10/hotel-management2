import { Routes } from '@angular/router';
import { authGuard } from './core/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./pages/login/login.component').then(m => m.LoginComponent)
  },
  {
    path: '',
    loadComponent: () => import('./pages/shell/shell.component').then(m => m.ShellComponent),
    canActivate: [authGuard],
    children: [
      { path: '', redirectTo: 'home', pathMatch: 'full' },
      {
        path: 'home',
        loadComponent: () => import('./pages/home/home.component').then(m => m.HomeComponent)
      },
      {
        path: 'rooms',
        loadComponent: () => import('./pages/rooms/rooms.component').then(m => m.RoomsComponent)
      },
      {
        path: 'guests',
        loadComponent: () => import('./pages/guests/guests.component').then(m => m.GuestsComponent)
      },
      {
        path: 'reservations',
        loadComponent: () =>
          import('./pages/reservations/reservations.component').then(m => m.ReservationsComponent)
      },
      {
        path: 'payments',
        loadComponent: () => import('./pages/payments/payments.component').then(m => m.PaymentsComponent)
      }
    ]
  },
  { path: '**', redirectTo: '' }
];

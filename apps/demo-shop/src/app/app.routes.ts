import type { Routes } from '@angular/router';
import { flagwireGuard } from '@flagwire/angular';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    title: 'Brew Bench',
    loadComponent: () => import('./storefront/storefront').then((module) => module.Storefront),
  },
  {
    path: 'recommendations',
    title: 'Recommendations, Brew Bench',
    canMatch: [flagwireGuard('beta-recommendations', { redirectTo: '/' })],
    loadComponent: () => import('./recommendations/recommendations').then((module) => module.Recommendations),
  },
  {
    path: '**',
    title: 'Not found, Brew Bench',
    loadComponent: () => import('./not-found/not-found').then((module) => module.NotFound),
  },
];

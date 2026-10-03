import type { Routes } from '@angular/router';
import { unsavedChangesGuard } from './shared/unsaved-changes.guard';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'flags' },
  {
    path: 'flags',
    title: 'Flags',
    loadComponent: () => import('./features/flags/flag-list').then((module) => module.FlagList),
  },
  {
    path: 'flags/new',
    title: 'New flag',
    loadComponent: () => import('./features/flags/new-flag').then((module) => module.NewFlag),
  },
  {
    path: 'flags/:key',
    title: 'Edit flag',
    canDeactivate: [unsavedChangesGuard],
    loadComponent: () => import('./features/flags/flag-editor').then((module) => module.FlagEditor),
  },
  {
    path: 'segments',
    title: 'Segments',
    loadComponent: () => import('./features/segments/segments').then((module) => module.Segments),
  },
  {
    path: 'environments',
    title: 'Environments',
    loadComponent: () => import('./features/environments/environments').then((module) => module.Environments),
  },
  {
    path: 'audit',
    title: 'Audit log',
    loadComponent: () => import('./features/audit/audit-log').then((module) => module.AuditLog),
  },
  {
    path: 'propagation',
    title: 'Propagation monitor',
    loadComponent: () =>
      import('./features/propagation/propagation-monitor').then((module) => module.PropagationMonitor),
  },
  {
    path: '**',
    title: 'Not found',
    loadComponent: () => import('./shared/not-found').then((module) => module.NotFound),
  },
];

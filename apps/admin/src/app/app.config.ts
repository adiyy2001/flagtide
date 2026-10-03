import { provideBrowserGlobalErrorListeners } from '@angular/core';
import type { ApplicationConfig } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { RUNTIME_CONFIG } from './api/runtime-config';
import type { RuntimeConfig } from './api/runtime-config';
import { routes } from './app.routes';

export function appConfig(config: RuntimeConfig): ApplicationConfig {
  return {
    providers: [
      provideBrowserGlobalErrorListeners(),
      { provide: RUNTIME_CONFIG, useValue: config },
      provideRouter(routes, withComponentInputBinding()),
    ],
  };
}

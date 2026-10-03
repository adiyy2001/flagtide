import { bootstrapApplication } from '@angular/platform-browser';
import { App } from './app/app';
import { appConfig } from './app/app.config';
import { loadRuntimeConfig } from './app/api/runtime-config';

loadRuntimeConfig()
  .then((config) => bootstrapApplication(App, appConfig(config)))
  .catch((failure: unknown) => {
    const message = failure instanceof Error ? failure.message : String(failure);
    const root = document.querySelector('admin-root');
    if (root !== null) {
      root.textContent = `The admin could not start: ${message}`;
    }
  });

import { bootstrapApplication } from '@angular/platform-browser';
import { App } from './app/app';
import { appConfig } from './app/app.config';
import { loadShopConfig } from './app/shop-config';

loadShopConfig()
  .then((config) => bootstrapApplication(App, appConfig(config)))
  .catch((failure: unknown) => {
    const message = failure instanceof Error ? failure.message : String(failure);
    const root = document.querySelector('shop-root');
    if (root !== null) {
      root.insertAdjacentText('afterbegin', `The shop could not start: ${message}`);
    }
  });

import { mergeApplicationConfig } from '@angular/core';
import type { ApplicationConfig } from '@angular/core';
import { provideServerRendering, withRoutes } from '@angular/ssr';
import { appConfig } from './app.config';
import { serverRoutes } from './app.routes.server';
import type { ShopConfig } from './shop-config';

export function serverAppConfig(config: ShopConfig): ApplicationConfig {
  return mergeApplicationConfig(appConfig(config), {
    providers: [provideServerRendering(withRoutes(serverRoutes))],
  });
}

import { provideBrowserGlobalErrorListeners } from '@angular/core';
import type { ApplicationConfig } from '@angular/core';
import { provideClientHydration } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';
import { provideFlagtide } from '@flagtide/angular';
import type { EvaluationContext } from '@flagtide/angular';
import { routes } from './app.routes';
import type { ShopConfig } from './shop-config';
import { injectVisitor, toEvaluationContext } from './visitor';

function currentContext(): EvaluationContext {
  return toEvaluationContext(injectVisitor());
}

export function appConfig(config: ShopConfig): ApplicationConfig {
  return {
    providers: [
      provideBrowserGlobalErrorListeners(),
      provideRouter(routes),
      provideClientHydration(),
      provideFlagtide({
        sdkKey: config.sdkKey,
        streamUrl: config.streamUrl,
        snapshotUrl: config.snapshotUrl,
        context: currentContext,
      }),
    ],
  };
}

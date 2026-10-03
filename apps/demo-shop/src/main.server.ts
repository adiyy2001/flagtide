import { bootstrapApplication } from '@angular/platform-browser';
import type { BootstrapContext } from '@angular/platform-browser';
import { App } from './app/app';
import { serverAppConfig } from './app/app.config.server';
import { shopConfigFromEnv } from './app/shop-config';

const config = shopConfigFromEnv(process.env);

const bootstrap = (context: BootstrapContext) => bootstrapApplication(App, serverAppConfig(config), context);

export default bootstrap;

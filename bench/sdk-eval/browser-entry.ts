import { measureScenarios } from './measure';

declare global {
  interface Window {
    runSdkEval: typeof measureScenarios;
  }
}

window.runSdkEval = measureScenarios;

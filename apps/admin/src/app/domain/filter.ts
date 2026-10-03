import type { FlagFilter, FlagModel } from './models';

export const EMPTY_FILTER: FlagFilter = { text: '', type: 'all', state: 'all' };

function matchesText(flag: FlagModel, text: string): boolean {
  const needle = text.trim().toLowerCase();
  return (
    needle === '' ||
    flag.key.toLowerCase().includes(needle) ||
    flag.description.toLowerCase().includes(needle)
  );
}

function matchesState(flag: FlagModel, state: FlagFilter['state'], environment: string): boolean {
  if (state === 'archived') {
    return flag.archived;
  }
  if (flag.archived) {
    return false;
  }
  const config = flag.environments[environment];
  switch (state) {
    case 'all':
      return true;
    case 'enabled':
      return config?.enabled === true && !config.killSwitch;
    case 'disabled':
      return config !== undefined && !config.enabled;
    case 'killed':
      return config?.killSwitch === true;
  }
}

export function filterFlags(
  flags: readonly FlagModel[],
  filter: FlagFilter,
  environment: string,
): FlagModel[] {
  return flags.filter(
    (flag) =>
      matchesText(flag, filter.text) &&
      (filter.type === 'all' || flag.type === filter.type) &&
      matchesState(flag, filter.state, environment),
  );
}

export function isFiltered(filter: FlagFilter): boolean {
  return filter.text.trim() !== '' || filter.type !== 'all' || filter.state !== 'all';
}

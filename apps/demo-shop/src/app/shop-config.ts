export interface ShopConfig {
  readonly sdkKey: string;
  readonly streamUrl: string;
  readonly snapshotUrl?: string;
}

export const DEFAULT_SHOP_CONFIG: ShopConfig = {
  sdkKey: 'fws_demo_dev_sdk_0000000000000',
  streamUrl: 'ws://127.0.0.1:18082/sdk/v1/stream',
  snapshotUrl: 'http://127.0.0.1:18082',
};

type Environment = Readonly<Record<string, string | undefined>>;

function present(value: string | undefined): value is string {
  return value !== undefined && value.trim() !== '';
}

export function shopConfigFromEnv(env: Environment): ShopConfig {
  return {
    sdkKey: present(env['FLAGWIRE_SHOP_SDK_KEY']) ? env['FLAGWIRE_SHOP_SDK_KEY'] : DEFAULT_SHOP_CONFIG.sdkKey,
    streamUrl: present(env['FLAGWIRE_SHOP_STREAM_URL'])
      ? env['FLAGWIRE_SHOP_STREAM_URL']
      : DEFAULT_SHOP_CONFIG.streamUrl,
    snapshotUrl: present(env['FLAGWIRE_SHOP_SNAPSHOT_URL'])
      ? env['FLAGWIRE_SHOP_SNAPSHOT_URL']
      : DEFAULT_SHOP_CONFIG.snapshotUrl,
  };
}

export function browserConfig(config: ShopConfig): Pick<ShopConfig, 'sdkKey' | 'streamUrl'> {
  return { sdkKey: config.sdkKey, streamUrl: config.streamUrl };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

export function parseShopConfig(raw: unknown): ShopConfig {
  if (!isRecord(raw)) {
    throw new Error('config.json must hold an object');
  }
  const { sdkKey, streamUrl } = raw;
  if (typeof sdkKey !== 'string' || sdkKey === '') {
    throw new Error('config.json needs sdkKey');
  }
  if (typeof streamUrl !== 'string' || !/^wss?:\/\//u.test(streamUrl)) {
    throw new Error('config.json needs a ws or wss streamUrl');
  }
  return { sdkKey, streamUrl };
}

export async function loadShopConfig(
  url = 'config.json',
  fetcher: typeof fetch = (input, init) => fetch(input, init),
): Promise<ShopConfig> {
  const response = await fetcher(url, { cache: 'no-store' });
  if (!response.ok) {
    throw new Error(`config.json answered ${response.status}`);
  }
  return parseShopConfig(await response.json());
}

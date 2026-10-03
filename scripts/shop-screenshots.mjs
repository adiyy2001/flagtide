import { mkdirSync } from 'node:fs';
import { resolve } from 'node:path';
import { chromium } from 'playwright';

const shopUrl = process.env.SHOP_URL ?? 'http://127.0.0.1:14300';
const apiUrl = process.env.API_URL ?? 'http://127.0.0.1:18081';
const adminKey = process.env.ADMIN_KEY ?? 'fwa_demo_dev_admin_000000000000';
const outDir = resolve(process.argv[2] ?? 'tmp/shop-shots');

mkdirSync(outDir, { recursive: true });

async function setEnabled(flag, enabled) {
  const response = await fetch(`${apiUrl}/api/v1/projects/demo/flags/${flag}/environments/dev/enabled`, {
    method: 'PUT',
    headers: { Authorization: `Bearer ${adminKey}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({ enabled }),
  });
  if (!response.ok) {
    throw new Error(`${flag} answered ${response.status}`);
  }
}

const browser = await chromium.launch({ executablePath: '/usr/bin/google-chrome', headless: true });
try {
  const context = await browser.newContext({ viewport: { width: 1280, height: 860 } });
  const page = await context.newPage();
  await page.goto(shopUrl);
  await page.getByText('Flags: Live').waitFor();
  await page.screenshot({ path: `${outDir}/shop-default.png` });

  await page.getByTestId('add-kettle').click();
  await page.getByTestId('add-grinder').click();
  await page.screenshot({ path: `${outDir}/shop-cart.png` });

  await page.getByRole('button', { name: /Flags/ }).click();
  await page.screenshot({ path: `${outDir}/shop-overrides.png` });

  await setEnabled('promo-banner', false);
  await page.getByTestId('promo-banner').waitFor({ state: 'detached' });
  await page.screenshot({ path: `${outDir}/shop-banner-off.png` });
  await setEnabled('promo-banner', true);

  await page.goto(`${shopUrl}/?visitor=visitor-beta&country=DE&plan=pro`);
  await page.getByText('Flags: Live').waitFor();
  await page.screenshot({ path: `${outDir}/shop-beta-visitor.png` });

  const narrow = await browser.newContext({ viewport: { width: 390, height: 844 } });
  const phone = await narrow.newPage();
  await phone.goto(shopUrl);
  await phone.getByText('Flags: Live').waitFor();
  await phone.screenshot({ path: `${outDir}/shop-phone.png`, fullPage: true });
} finally {
  await browser.close();
}
console.log(`screenshots in ${outDir}`);

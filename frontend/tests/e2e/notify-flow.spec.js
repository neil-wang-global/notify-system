import { test, expect } from '@playwright/test';

test('strategies page loads and shows form', async ({ page }) => {
  await page.goto('/');
  // Click strategies tab
  await page.click('text=策略管理');
  // Verify form elements exist
  await expect(page.locator('input, select').first()).toBeVisible();
});

test('notifications page loads', async ({ page }) => {
  await page.goto('/');
  await page.click('text=通知记录');
  await expect(page.locator('.grid, .text-slate-400').first()).toBeVisible();
});

test('system monitor loads', async ({ page }) => {
  await page.goto('/');
  await page.click('text=系统监控');
  await expect(page.locator('text=Kafka')).toBeVisible();
});

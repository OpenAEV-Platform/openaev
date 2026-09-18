import { execSync } from 'node:child_process';
import os from 'node:os';

import { type Browser, expect, type Page } from '@playwright/test';

import AgentInstallPage from '../model/agents/AgentInstallPage';
import EndpointListPage from '../model/assets/EndpointListPage';
import { AUTH_FILE } from './constants';
import { tenantUrl } from './url';

const APP_URL = process.env.APP_URL ?? 'http://localhost:3001';

export interface InstalledAgent {
  hostname: string;
  platform: string;
}

export const getAgentPlatform = (): string => {
  switch (os.platform()) {
    case 'win32': return 'Windows';
    case 'darwin': return 'MacOS';
    default: return 'Linux';
  }
};

export const executeAgentInstall = (installCommand: string): void => {
  // Windows PowerShell 5.1 prompts for confirmation when iwr parses HTML.
  const commandToExecute = os.platform() === 'win32'
    ? installCommand.replace(/\b(iwr|Invoke-WebRequest)\b/, '$1 -UseBasicParsing')
    : installCommand;

  execSync(commandToExecute, {
    stdio: 'inherit',
    timeout: 60_000,
    shell: os.platform() === 'win32' ? 'powershell' : undefined,
  });
};

export const installAgent = async (browser: Browser): Promise<InstalledAgent> => {
  const platform = getAgentPlatform();
  const hostname = os.hostname().toLowerCase();
  const context = await browser.newContext({
    storageState: AUTH_FILE,
    baseURL: APP_URL,
  });

  try {
    const page = await context.newPage();
    await page.goto(tenantUrl('/admin/agents'));
    const agentInstallPage = new AgentInstallPage(page);
    await agentInstallPage.waitForLoad();
    const installCommand = await agentInstallPage.getInstallCommand(platform);
    executeAgentInstall(installCommand);
  } finally {
    await context.close();
  }

  return {
    hostname,
    platform,
  };
};

export const waitForRegisteredAgent = async (page: Page, hostname: string): Promise<void> => {
  await expect(async () => {
    await page.goto(tenantUrl('/admin/assets'));
    const endpointList = new EndpointListPage(page);
    await endpointList.waitForLoad();
    await expect(endpointList.getEndpointByHostname(hostname)).toBeVisible();
  }).toPass({
    intervals: [5_000],
    timeout: 150_000,
  });
};

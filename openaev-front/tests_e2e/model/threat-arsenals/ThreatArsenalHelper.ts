import { expect, type Page } from '@playwright/test';

import LeftMenuComponent from '../LeftMenuComponent';
import ThreatArsenalFormComponent from './ThreatArsenalFormComponent';
import ThreatArsenalListPage from './ThreatArsenalListPage';

export interface CommandLinePayloadOptions {
  name: string;
  command: string;
  platform: string;
  /** @default 'Bash' for non-Windows, 'PowerShell' for Windows */
  executor?: string;
}

/**
 * Helper to create a command-line payload via the Threat Arsenal UI.
 * Navigates from any page → Threat Arsenal → Create form → fills & saves.
 */
class ThreatArsenalHelper {
  constructor(private page: Page) {}

  /**
   * Creates a Command Line payload in Threat Arsenal and asserts success.
   */
  async createCommandLinePayload(options: CommandLinePayloadOptions): Promise<void> {
    const { name, command, platform, executor } = options;
    const resolvedExecutor = executor ?? (platform === 'Windows' ? 'PowerShell' : 'Bash');

    const leftMenu = new LeftMenuComponent(this.page);
    await leftMenu.goToThreatArsenal();

    const threatArsenalList = new ThreatArsenalListPage(this.page);
    await threatArsenalList.waitForLoad();
    await threatArsenalList.openCreateThreatArsenal();

    const form = new ThreatArsenalFormComponent(this.page);
    await form.nameField.fill(name);
    await form.selectDomain('Endpoint');
    await form.switchToCommandsTab();
    await form.selectCommandType('Command Line');
    await form.selectPlatform(platform);
    await form.selectExecutor(resolvedExecutor);
    await form.commandField.fill(command);
    await form.switchToGeneralTab();
    await form.save();

    await expect(
      this.page.getByText('The element has been successfully created'),
    ).toBeVisible();
  }
}

export default ThreatArsenalHelper;


import { type Locator, type Page } from '@playwright/test';

import MuiFormHelpers from '../../utils/MuiFormHelpers';

class PayloadFormComponent {
  readonly page: Page;

  // Form tabs
  readonly generalTab: Locator;
  readonly commandsTab: Locator;

  // General tab fields
  readonly nameField: Locator;
  readonly descriptionField: Locator;
  readonly attackPatternsField: Locator;
  readonly tagsField: Locator;
  readonly expectationsField: Locator;
  readonly domainsField: Locator;

  // Commands tab fields
  readonly typeField: Locator;
  readonly architectureField: Locator;
  readonly platformsField: Locator;
  readonly argumentBtn: Locator;
  readonly prerequisiteBtn: Locator;
  readonly executorField: Locator;
  readonly commandField: Locator;
  readonly documentsAddBtn: Locator;
  readonly hostnameField: Locator;

  // Form actions
  readonly saveButton: Locator;

  constructor(page: Page) {
    this.page = page;

    // Tabs
    this.generalTab = page.getByRole('tab', { name: 'General' });
    this.commandsTab = page.getByRole('tab', { name: 'Commands' });

    // General fields
    this.nameField = page.getByRole('textbox', { name: 'Name*' });
    this.descriptionField = page.getByRole('textbox', { name: 'Description' });
    this.attackPatternsField = page.getByRole('combobox', { name: 'Attack patterns' });
    this.tagsField = page.getByRole('combobox', { name: 'Tags' });
    this.domainsField = page.getByRole('combobox', { name: 'Domains' });
    this.expectationsField = page.getByRole('combobox', { name: 'Expectations *' });

    // Commands fields
    this.typeField = page.getByRole('combobox', { name: 'Type *' });
    this.architectureField = page.getByRole('combobox', { name: 'Architecture *' });
    this.platformsField = page.getByRole('combobox', { name: 'Platforms' });
    this.argumentBtn = page.getByRole('button', { name: 'New argument' });
    this.prerequisiteBtn = page.getByRole('button', { name: 'New prerequisite' });
    this.executorField = page.getByRole('combobox', { name: 'Executor *' });
    this.commandField = page.locator('textarea[name="command_content"]');
    this.documentsAddBtn = page.getByText('Add document');
    this.hostnameField = page.getByRole('textbox', { name: 'Hostname*' });

    // Actions
    this.saveButton = page.getByRole('button', { name: 'Create' });
  }

  // -- Get Locator methods

  getArgumentValue(index: number, field: string) {
    return this.page
      .locator(`[name="payload_arguments.${index}.${field}"]`)
      .inputValue();
  }

  // -- Action methods

  async switchToCommandsTab() {
    await this.commandsTab.click();
  };

  async selectDomain(domains: string | string[]) {
    const values = Array.isArray(domains) ? domains : [domains];

    await Promise.all(
      values.map(async (value) => {
        await this.domainsField.click();
        await this.domainsField.fill(value);

        const option = this.page.getByRole('option', { name: value });

        const selected = await option.getAttribute('aria-selected');
        if (selected !== 'true') {
          await option.click();
        }
      }),
    );
  }

  async selectCommandType(type: string) {
    await MuiFormHelpers.selectSingleOption(this.page, this.typeField, type);
  }

  async selectPlatform(platform: string) {
    await MuiFormHelpers.selectSingleOption(this.page, this.platformsField, platform);
  }

  async selectArchitecture(architecture: string) {
    await MuiFormHelpers.selectSingleOption(this.page, this.architectureField, architecture);
  }

  async selectExecutor(architecture: string) {
    await MuiFormHelpers.selectSingleOption(this.page, this.executorField, architecture);
  }

  async getArchitectureOptions() {
    return MuiFormHelpers.getSelectFieldOption(this.page, this.architectureField);
  }

  async addArgument() {
    await this.argumentBtn.click();
  }

  async fillArgument(index: number, data: {
    type?: string;
    key?: string;
    defaultValue?: string;
  }) {
    const prefix = `payload_arguments.${index}`;
    if (data.key) {
      await this.page.locator(`[name="${prefix}.key"]`).fill(data.key);
    }
    if (data.type) {
      // There is already one type field for the type of command
      await this.page.getByRole('combobox', { name: 'Type *' }).nth(index + 1).click();
      await this.page.getByRole('option', { name: data.type }).click();
    }
    if (data.defaultValue) {
      await this.page.locator(`[name="${prefix}.default_value"]`).fill(data.defaultValue);
    }
  }

  async removeArgument(index: number) {
    await this.page
      .getByTestId(`payload_arguments.${index}.delete-btn`)
      .click();
  }

  async save() {
    await this.saveButton.click();
  }
}

export default PayloadFormComponent;

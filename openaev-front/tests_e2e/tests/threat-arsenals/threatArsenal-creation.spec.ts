import { expect, type Locator, test } from '@playwright/test';

import LeftMenuComponent from '../../model/LeftMenuComponent';
import ThreatArsenalFormComponent from '../../model/threat-arsenals/ThreatArsenalFormComponent';
import ThreatArsenalListPage from '../../model/threat-arsenals/ThreatArsenalListPage';
import MuiFormHelpers from '../../utils/MuiFormHelpers';
import {
  ArchitectureConfigs, Architectures,
  CommandTypeFields,
  GeneralTabFields,
  ThreatArsenalCommandTypes, type ThreatArsenalFormFields,
} from '../../utils/threatArsenal.constants';
import { tenantUrl } from '../../utils/url';

test.describe('Threat Arsenal form', () => {
  let leftMenu: LeftMenuComponent;
  let threatArsenalList: ThreatArsenalListPage;
  let threatArsenalForm: ThreatArsenalFormComponent;

  test.beforeEach(async ({ page }) => {
    // Initialize all page objects
    leftMenu = new LeftMenuComponent(page);
    threatArsenalList = new ThreatArsenalListPage(page);
    threatArsenalForm = new ThreatArsenalFormComponent(page);

    // Navigate to application
    await page.goto(tenantUrl('/admin'));

    // Navigate to threat arsenal section
    await leftMenu.goToThreatArsenal();
    await threatArsenalList.waitForLoad();
    // Open create threat arsenal drawer
    await threatArsenalList.openCreateThreatArsenal();
  });

  test.describe('Visible fields', () => {
    test('General tab - should display all fields', async () => {
      await Promise.all(
        [...GeneralTabFields.requiredFields, ...GeneralTabFields.optionalFields].map(fieldName =>
          expect(threatArsenalForm[fieldName as ThreatArsenalFormFields] as Locator).toBeVisible(),
        ),
      );
    });

    test.describe('Commands tab - fields per command type', () => {
      Object.entries(ThreatArsenalCommandTypes).forEach(([_, commandTypeLabel]) => {
        test(`${commandTypeLabel} - should display correct fields`, async () => {
          await threatArsenalForm.switchToCommandsTab();
          await threatArsenalForm.selectCommandType(commandTypeLabel);
          // Check common fields
          await Promise.all(
            CommandTypeFields.common.map(fieldName =>
              expect(threatArsenalForm[fieldName as ThreatArsenalFormFields] as Locator).toBeVisible(),
            ),
          );
          await Promise.all(
            CommandTypeFields[commandTypeLabel].map(fieldName =>
              expect(threatArsenalForm[fieldName as ThreatArsenalFormFields] as Locator).toBeVisible(),
            ),
          );
        });
      });
    });

    test.describe('Architecture Field Behavior', () => {
      ArchitectureConfigs.forEach(({ commandType, expectedOptions, defaultValue }) => {
        if (expectedOptions) {
          test(`${commandType} - architecture configuration with options`, async () => {
            await threatArsenalForm.switchToCommandsTab();
            await threatArsenalForm.selectCommandType(commandType);
            const actualOptions = await threatArsenalForm.getArchitectureOptions();
            expect(actualOptions).toEqual(expectedOptions);
          });
        } else {
          test(`${commandType} - architecture configuration disabled`, async () => {
            await threatArsenalForm.switchToCommandsTab();
            await threatArsenalForm.selectCommandType(commandType);
            await expect(threatArsenalForm.architectureField).toBeDisabled();
            await expect(threatArsenalForm.architectureField).toContainText(defaultValue);
          });
        }
      });

      test('should reset architecture when switching from Executable to DNS Resolution', async () => {
        // Setup initial state
        await threatArsenalForm.switchToCommandsTab();
        await threatArsenalForm.selectCommandType(ThreatArsenalCommandTypes.EXECUTABLE);
        await threatArsenalForm.selectArchitecture(Architectures.ARM64);

        // Switch command type
        await threatArsenalForm.selectCommandType(ThreatArsenalCommandTypes.DNS_RESOLUTION);

        // Verify reset
        await expect(threatArsenalForm.architectureField).toBeDisabled();
        await expect(threatArsenalForm.architectureField).toContainText(Architectures.ALL);
      });
    });

    test.describe('Argument Management', () => {
      test('should add, modify, and remove arguments', async () => {
        await threatArsenalForm.switchToCommandsTab();
        await threatArsenalForm.selectCommandType(ThreatArsenalCommandTypes.EXECUTABLE);

        // Add first argument
        await threatArsenalForm.addArgument();
        await threatArsenalForm.fillArgument(0, {
          type: 'Text',
          key: 'arg1',
          defaultValue: 'default1',
        });

        // Add second argument
        await threatArsenalForm.addArgument();
        await threatArsenalForm.fillArgument(1, {
          type: 'Document',
          key: 'arg2',
        });

        // Verify both arguments
        expect(await threatArsenalForm.getArgumentValue(0, 'key')).toBe('arg1');
        expect(await threatArsenalForm.getArgumentValue(1, 'key')).toBe('arg2');

        // Remove first and verify reordering
        await threatArsenalForm.removeArgument(0);
        expect(await threatArsenalForm.getArgumentValue(0, 'key')).toBe('arg2');
      });
    });
  });

  test.describe('Form Validation', () => {
    Object.entries(ThreatArsenalCommandTypes).forEach(([_, commandTypeLabel]) => {
      test(`${commandTypeLabel} - should validate required fields`, async () => {
        await threatArsenalForm.switchToCommandsTab();
        await threatArsenalForm.selectCommandType(commandTypeLabel);
        await threatArsenalForm.save();
        await threatArsenalForm.switchToCommandsTab();

        await Promise.all([
          expect(MuiFormHelpers.getFieldError(threatArsenalForm.platformsField)).toHaveText('Should not be empty'),
          ...CommandTypeFields[commandTypeLabel].map((fieldName) => {
            const locator = fieldName === 'documentsAddBtn'
              ? MuiFormHelpers.getListContainer(threatArsenalForm[fieldName as ThreatArsenalFormFields] as Locator)
              : MuiFormHelpers.getFieldError(threatArsenalForm[fieldName as ThreatArsenalFormFields] as Locator);

            return expect(locator).toContainText('Should not be empty');
          }),
        ]);
      });
    });

    test('Arguments - should validate required fields', async () => {
      await threatArsenalForm.switchToCommandsTab();
      await threatArsenalForm.selectCommandType(ThreatArsenalCommandTypes.EXECUTABLE);

      await threatArsenalForm.addArgument();
      await threatArsenalForm.save();
      await threatArsenalForm.switchToCommandsTab();

      await expect(MuiFormHelpers.getFieldError(threatArsenalForm.page.locator('[name="action_arguments.0.key"]'))).toHaveText('Should not be empty');
      await expect(MuiFormHelpers.getFieldError(threatArsenalForm.page.locator('[name="action_arguments.0.default_value"]'))).toHaveText('Should not be empty');
    });

    test('Tab navigation - should redirect to tab with errors', async () => {
      // Fill Commands tab partially
      await threatArsenalForm.switchToCommandsTab();
      await threatArsenalForm.selectCommandType('Command Line');
      await threatArsenalForm.commandField.fill('echo test');
      await threatArsenalForm.selectArchitecture('x86_64');
      await threatArsenalForm.selectPlatform('Windows');

      // Try to save (General tab is empty)
      await threatArsenalForm.save();
      await threatArsenalForm.switchToCommandsTab();

      await expect(MuiFormHelpers.getFieldError(threatArsenalForm.executorField)).toHaveText('Should not be empty');
      await threatArsenalForm.selectExecutor('PowerShell');
      await threatArsenalForm.save();
      await threatArsenalForm.save();

      // Should show error on General tab
      await expect(MuiFormHelpers.getFieldError(threatArsenalForm.nameField)).toHaveText('Should not be empty');
    });
  });

  test('Complete Workflows - should create Command Line threat arsenal action successfully', async ({ page }) => {
    const actionName = `Test Threat Arsenal ${Date.now()}`;
    await threatArsenalForm.nameField.fill(actionName);
    await threatArsenalForm.selectDomain('Cloud');
    await threatArsenalForm.switchToCommandsTab();
    await threatArsenalForm.selectCommandType('Command Line');
    await threatArsenalForm.selectPlatform('Windows');
    await threatArsenalForm.selectExecutor('PowerShell');
    await threatArsenalForm.commandField.fill('echo test');

    await threatArsenalForm.switchToGeneralTab();
    await threatArsenalForm.save();
    await expect(page.getByText('The element has been successfully created')).toBeVisible();
    await expect(threatArsenalList.addButton).toBeVisible();
    await threatArsenalList.searchThreatArsenal(actionName);
    await expect(threatArsenalList.getItem(1)).toContainText(actionName);
  });
});

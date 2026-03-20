import { expect, type Locator, test } from '@playwright/test';

import LeftMenuComponent from '../../model/LeftMenuComponent';
import PayloadFormComponent from '../../model/payloads/PayloadFormComponent';
import PayloadListPage from '../../model/payloads/PayloadListPage';
import MuiFormHelpers from '../../utils/MuiFormHelpers';
import {
  ArchitectureConfigs, Architectures,
  CommandTypeFields,
  GeneralTabFields,
  PayloadCommandTypes, type PayloadFormFields,
} from '../../utils/payload.constants';

test.describe('Payload form', () => {
  let leftMenu: LeftMenuComponent;
  let payloadList: PayloadListPage;
  let payloadForm: PayloadFormComponent;

  test.beforeEach(async ({ page }) => {
    // Initialize all page objects
    leftMenu = new LeftMenuComponent(page);
    payloadList = new PayloadListPage(page);
    payloadForm = new PayloadFormComponent(page);

    // Navigate to application
    await page.goto('/');

    // Navigate to payloads section
    await leftMenu.goToPayloads();
    await payloadList.waitForLoad();
    // Open create payload drawer
    await payloadList.openCreatePayload();
  });

  test.describe('Visible fields', () => {
    test('General tab - should display all fields', async () => {
      await Promise.all(
        [...GeneralTabFields.requiredFields, ...GeneralTabFields.optionalFields].map(fieldName =>
          expect(payloadForm[fieldName as PayloadFormFields] as Locator).toBeVisible(),
        ),
      );
    });

    test.describe('Commands tab - fields per command type', () => {
      Object.entries(PayloadCommandTypes).forEach(([_, commandTypeLabel]) => {
        test(`${commandTypeLabel} - should display correct fields`, async () => {
          await payloadForm.switchToCommandsTab();
          await payloadForm.selectCommandType(commandTypeLabel);
          // Check common fields
          await Promise.all(
            CommandTypeFields.common.map(fieldName =>
              expect(payloadForm[fieldName as PayloadFormFields] as Locator).toBeVisible(),
            ),
          );
          await Promise.all(
            CommandTypeFields[commandTypeLabel].map(fieldName =>
              expect(payloadForm[fieldName] as Locator).toBeVisible(),
            ),
          );
        });
      });
    });

    test.describe('Architecture Field Behavior', () => {
      ArchitectureConfigs.forEach(({ commandType, expectedOptions, defaultValue }) => {
        if (expectedOptions) {
          test(`${commandType} - architecture configuration with options`, async () => {
            await payloadForm.switchToCommandsTab();
            await payloadForm.selectCommandType(commandType);
            const actualOptions = await payloadForm.getArchitectureOptions();
            expect(actualOptions).toEqual(expectedOptions);
          });
        } else {
          test(`${commandType} - architecture configuration disabled`, async () => {
            await payloadForm.switchToCommandsTab();
            await payloadForm.selectCommandType(commandType);
            await expect(payloadForm.architectureField).toBeDisabled();
            await expect(payloadForm.architectureField).toContainText(defaultValue);
          });
        }
      });

      test('should reset architecture when switching from Executable to DNS Resolution', async () => {
        // Setup initial state
        await payloadForm.switchToCommandsTab();
        await payloadForm.selectCommandType(PayloadCommandTypes.EXECUTABLE);
        await payloadForm.selectArchitecture(Architectures.ARM64);

        // Switch command type
        await payloadForm.selectCommandType(PayloadCommandTypes.DNS_RESOLUTION);

        // Verify reset
        await expect(payloadForm.architectureField).toBeDisabled();
        await expect(payloadForm.architectureField).toContainText(Architectures.ALL);
      });
    });

    test.describe('Argument Management', () => {
      test('should add, modify, and remove arguments', async () => {
        await payloadForm.switchToCommandsTab();
        await payloadForm.selectCommandType(PayloadCommandTypes.EXECUTABLE);

        // Add first argument
        await payloadForm.addArgument();
        await payloadForm.fillArgument(0, {
          type: 'Text',
          key: 'arg1',
          defaultValue: 'default1',
        });

        // Add second argument
        await payloadForm.addArgument();
        await payloadForm.fillArgument(1, {
          type: 'Document',
          key: 'arg2',
        });

        // Verify both arguments
        expect(await payloadForm.getArgumentValue(0, 'key')).toBe('arg1');
        expect(await payloadForm.getArgumentValue(1, 'key')).toBe('arg2');

        // Remove first and verify reordering
        await payloadForm.removeArgument(0);
        expect(await payloadForm.getArgumentValue(0, 'key')).toBe('arg2');
      });
    });
  });

  test.describe('Form Validation', () => {
    Object.entries(PayloadCommandTypes).forEach(([_, commandTypeLabel]) => {
      test(`${commandTypeLabel} - should validate required fields`, async () => {
        await payloadForm.switchToCommandsTab();
        await payloadForm.selectCommandType(commandTypeLabel);
        await payloadForm.save();
        await payloadForm.switchToCommandsTab();

        await Promise.all([
          expect(MuiFormHelpers.getFieldError(payloadForm.platformsField)).toHaveText('Should not be empty'),
          ...CommandTypeFields[commandTypeLabel].map((fieldName) => {
            const locator = fieldName === 'documentsAddBtn'
              ? MuiFormHelpers.getListContainer(payloadForm[fieldName] as Locator)
              : MuiFormHelpers.getFieldError(payloadForm[fieldName] as Locator);

            return expect(locator).toContainText('Should not be empty');
          }),
        ]);
      });
    });

    test('Arguments - should validate required fields', async () => {
      await payloadForm.switchToCommandsTab();
      await payloadForm.selectCommandType(PayloadCommandTypes.EXECUTABLE);

      await payloadForm.addArgument();
      await payloadForm.save();
      await payloadForm.switchToCommandsTab();

      await expect(MuiFormHelpers.getFieldError(payloadForm.page.locator(`[name="payload_arguments.0.key"]`))).toHaveText('Should not be empty');
      await expect(MuiFormHelpers.getFieldError(payloadForm.page.locator(`[name="payload_arguments.0.default_value"]`))).toHaveText('Should not be empty');
    });

    test('Tab navigation - should redirect to tab with errors', async () => {
      // Fill Commands tab partially
      await payloadForm.switchToCommandsTab();
      await payloadForm.selectCommandType('Command Line');
      await payloadForm.commandField.fill('echo test');
      await payloadForm.selectArchitecture('x86_64');
      await payloadForm.selectPlatform('Windows');

      // Try to save (General tab is empty)
      await payloadForm.save();
      await payloadForm.switchToCommandsTab();

      await expect(MuiFormHelpers.getFieldError(payloadForm.executorField)).toHaveText('Should not be empty');
      await payloadForm.selectExecutor('PowerShell');
      await payloadForm.save();
      await payloadForm.save();

      // Should show error on General tab
      await expect(MuiFormHelpers.getFieldError(payloadForm.nameField)).toHaveText('Should not be empty');
    });
  });

  test('Complete Workflows - should create Command Line payload successfully', async ({ page }) => {
    const payloadName = `Test Payload ${Date.now()}`;
    await payloadForm.nameField.fill(payloadName);
    await payloadForm.selectDomain('Cloud');
    await payloadForm.switchToCommandsTab();
    await payloadForm.selectCommandType('Command Line');
    await payloadForm.selectPlatform('Windows');
    await payloadForm.selectExecutor('PowerShell');
    await payloadForm.commandField.fill('echo test');

    await payloadForm.save();
    await expect(page.getByText('The element has been successfully updated')).toBeVisible();
    await expect(payloadList.addButton).toBeVisible();
    await payloadList.searchPayload(payloadName);
    await expect(payloadList.getItem(1)).toContainText(payloadName);
  });
});

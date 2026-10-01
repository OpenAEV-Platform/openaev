import { expect, type Page } from '@playwright/test';

import AtomicTestingApiHelpers from '../../api-helpers/AtomicTestingApiHelpers';
import CredentialApiHelpers from '../../api-helpers/CredentialApiHelpers';
import { CREDENTIAL_REFERENCE_FIELD_LABEL } from '../../api-helpers/InjectorContractApiHelpers';
import { test } from '../../fixtures';
import AtomicTestingPage from '../../model/atomic-testings/AtomicTestingPage';
import CredentialApiMock from '../../model/credentials/CredentialApiMock';
import InjectCredentialReferenceComponent from '../../model/injects/InjectCredentialReferenceComponent';

// Requires the CREDENTIAL_ASSET preview feature (enabled by the `ci` and `dev` backend profiles).
// The cloud injectors (e.g. "AWS - Detonate a custom Stratus technique") are external and not
// deployed on E2E environments: each test seeds a custom contract on the manual injector that
// declares the same `credential-reference` field.

/** Label rendered by CredentialStatusChip for a stored status. */
const statusLabel = (status?: string) => {
  if (!status || status === 'UNSET') {
    return '-';
  }
  return status === 'ACTIVE' ? 'Active' : 'Inactive';
};

/**
 * Records the body of the credential and inject API responses received by the page.
 * Long-lived streams (server-sent events) are skipped: their body never completes.
 */
const recordApiResponses = (page: Page) => {
  const bodies: Promise<string>[] = [];
  page.on('response', (response) => {
    const isRecorded = /\/api\/.*(credentials|injects|atomic-testings)/.test(response.url())
      && !(response.headers()['content-type'] ?? '').includes('text/event-stream');
    if (isRecorded) {
      bodies.push(response.text().catch(() => ''));
    }
  });
  return async () => (await Promise.all(bodies)).join('\n');
};

test.describe('Inject form - Credential reference', () => {
  const awsConstraint = { credentialField: { credentialReferenceType: 'CLOUD_AWS' as const } };

  let atomicTestingPage: AtomicTestingPage;
  let credentialSection: InjectCredentialReferenceComponent;
  // Shared by every credential seeded in a test: searching it in the picker keeps the assertions
  // independent from the credentials already present on the platform.
  let runId: string;

  const openInjectForm = async (atomicTestingId: string) => {
    await atomicTestingPage.goto(atomicTestingId);
    await atomicTestingPage.openUpdateInjectForm();
    await expect(credentialSection.form).toBeVisible();
  };

  const expectSecretsNotDisplayed = async (page: Page, secrets: string[]) => {
    await Promise.all(secrets.map(secret => expect(page.locator('body')).not.toContainText(secret)));
  };

  test.beforeEach(async ({ page }) => {
    await page.addInitScript(() => {
      const style = document.createElement('style');
      style.innerHTML = `
        *, *::before, *::after {
          transition: none !important;
          animation: none !important;
        }
      `;
      document.head.appendChild(style);
    });
    atomicTestingPage = new AtomicTestingPage(page);
    credentialSection = new InjectCredentialReferenceComponent(page);
    runId = `${Date.now()}-${Math.floor(Math.random() * 10000)}`;
  });

  test.describe('Credential configuration entry point', () => {
    test('should display the credential action when the contract declares a credential reference field', async ({ createAtomicTesting }) => {
      const atomicTesting = await createAtomicTesting(`Cloud inject ${runId}`, awsConstraint);

      await openInjectForm(atomicTesting.inject_id);

      await expect(credentialSection.getFieldLabel(CREDENTIAL_REFERENCE_FIELD_LABEL)).toBeVisible();
      await expect(credentialSection.updateCredentialButton).toBeVisible();
      await expect(credentialSection.updateCredentialButton).toBeEnabled();
    });

    test('should not display any credential selector when the contract has no credential reference field', async ({ createAtomicTesting }) => {
      const atomicTesting = await createAtomicTesting(`Inject without credential ${runId}`);

      await openInjectForm(atomicTesting.inject_id);

      await expect(credentialSection.submitButton).toBeVisible();
      await expect(credentialSection.getFieldLabel(CREDENTIAL_REFERENCE_FIELD_LABEL)).toHaveCount(0);
      await expect(credentialSection.updateCredentialButton).toHaveCount(0);
    });
  });

  test.describe('Credential selector', () => {
    test('should only display the credentials accepted by the contract, with the constraint as a chip', async ({ createAtomicTesting, createCredential }) => {
      const [awsProduction, identity] = await Promise.all([
        createCredential(`AWS Production ${runId}`, 'CLOUD_AWS'),
        createCredential(`Identity ${runId}`, 'IDENTITY'),
      ]);
      const atomicTesting = await createAtomicTesting(`AWS - Detonate a custom Stratus technique ${runId}`, awsConstraint);
      await openInjectForm(atomicTesting.inject_id);

      await credentialSection.openPicker();
      await credentialSection.picker.search(runId);

      await expect(credentialSection.picker.constraintChip).toBeVisible();
      await expect(credentialSection.picker.constraintChip).toContainText(/Cloud AWS|CLOUD_AWS/i);
      await expect(credentialSection.picker.getRow(awsProduction.credential_name)).toBeVisible();
      await expect(credentialSection.picker.getRow(identity.credential_name)).toHaveCount(0);
      await expect(credentialSection.picker.getRows()).toHaveCount(1);
    });

    [
      {
        status: 'ACTIVE' as const,
        label: 'Active',
      },
      {
        status: 'AUTH_FAILED' as const,
        label: 'Inactive',
      },
    ].forEach(({ status, label }) => {
      test(`should display the stored status of the credential: ${label}`, async ({ page, createAtomicTesting, createCredential }) => {
        const credential = await createCredential(`AWS ${label} ${runId}`, 'CLOUD_AWS');
        await new CredentialApiMock(page).mockStoredStatuses({ [credential.credential_id]: status });
        const atomicTesting = await createAtomicTesting(`Cloud inject ${runId}`, awsConstraint);
        await openInjectForm(atomicTesting.inject_id);

        await credentialSection.openPicker();
        await credentialSection.picker.search(runId);

        const row = credentialSection.picker.getRow(credential.credential_name);
        await expect(row).toBeVisible();
        await expect(row.getByText(label, { exact: true })).toBeVisible();
      });
    });

    test('should not offer any credential when none satisfies the constraint', async ({ createAtomicTesting, createCredential }) => {
      await Promise.all([
        createCredential(`AWS Production ${runId}`, 'CLOUD_AWS'),
        createCredential(`Identity ${runId}`, 'IDENTITY'),
      ]);
      const atomicTesting = await createAtomicTesting(`GCP inject ${runId}`, { credentialField: { credentialReferenceType: 'CLOUD_GCP' } });
      await openInjectForm(atomicTesting.inject_id);

      await credentialSection.openPicker();
      await credentialSection.picker.search(runId);

      await expect(credentialSection.picker.constraintChip).toBeVisible();
      await expect(credentialSection.picker.constraintChip).toContainText(/Cloud GCP|CLOUD_GCP/i);
      await expect(credentialSection.picker.getRows()).toHaveCount(0);
    });

    test('should display every credential once the constraint chip is removed', async ({ createAtomicTesting, createCredential }) => {
      const [awsProduction, identity] = await Promise.all([
        createCredential(`AWS Production ${runId}`, 'CLOUD_AWS'),
        createCredential(`Identity ${runId}`, 'IDENTITY'),
      ]);
      const atomicTesting = await createAtomicTesting(`Cloud inject ${runId}`, awsConstraint);
      await openInjectForm(atomicTesting.inject_id);
      await credentialSection.openPicker();
      await credentialSection.picker.search(runId);
      await expect(credentialSection.picker.getRow(identity.credential_name)).toHaveCount(0);

      await credentialSection.picker.removeConstraint();

      await expect(credentialSection.picker.constraintChip).toHaveCount(0);
      await expect(credentialSection.picker.getRow(awsProduction.credential_name)).toBeVisible();
      await expect(credentialSection.picker.getRow(identity.credential_name)).toBeVisible();
      // A credential that does not satisfy the constraint can still be selected
      await credentialSection.picker.select(identity.credential_name);
      await credentialSection.picker.submit();
      await expect(credentialSection.getSelectedCredential(identity.credential_name)).toBeVisible();
    });

    test('should offer every credential without any chip when the field declares no constraint', async ({ createAtomicTesting, createCredential }) => {
      const [awsProduction, identity] = await Promise.all([
        createCredential(`AWS Production ${runId}`, 'CLOUD_AWS'),
        createCredential(`Identity ${runId}`, 'IDENTITY'),
      ]);
      const atomicTesting = await createAtomicTesting(`Inject without constraint ${runId}`, { credentialField: {} });
      await openInjectForm(atomicTesting.inject_id);

      await credentialSection.openPicker();
      await credentialSection.picker.search(runId);

      await expect(credentialSection.picker.getRow(awsProduction.credential_name)).toBeVisible();
      await expect(credentialSection.picker.getRow(identity.credential_name)).toBeVisible();
      await expect(credentialSection.picker.getRows()).toHaveCount(2);
      await expect(credentialSection.picker.constraintChip).toHaveCount(0);
    });
  });

  test.describe('Credential association', () => {
    test('should select and persist a compatible credential', async ({ page, request, createAtomicTesting, createCredential }) => {
      const awsProduction = await createCredential(`AWS Production ${runId}`, 'CLOUD_AWS');
      const atomicTesting = await createAtomicTesting(`Cloud inject ${runId}`, awsConstraint);
      await openInjectForm(atomicTesting.inject_id);

      // Select the credential
      await credentialSection.openPicker();
      await credentialSection.picker.search(runId);
      await credentialSection.picker.select(awsProduction.credential_name);
      await credentialSection.picker.submit();
      await expect(credentialSection.getSelectedCredentials()).toHaveCount(1);
      await expect(credentialSection.getSelectedCredential(awsProduction.credential_name)).toBeVisible();
      await expectSecretsNotDisplayed(page, awsProduction.secrets);

      // Save the inject
      await credentialSection.save();

      // Only the credential reference is persisted on the inject
      const savedInject = await new AtomicTestingApiHelpers(request).getAtomicTesting(atomicTesting.inject_id);
      expect(savedInject.inject_secret_references).toEqual([awsProduction.credential_id]);
      const storedContent = JSON.stringify(savedInject.inject_content);
      awsProduction.secrets.forEach(secret => expect(storedContent).not.toContain(secret));
      expect(storedContent).not.toContain(awsProduction.credential_name);

      // The selection is still displayed when the form is reopened
      await atomicTestingPage.openUpdateInjectForm();
      await expect(credentialSection.getSelectedCredential(awsProduction.credential_name)).toBeVisible();
    });

    test('should display the persisted credential and its stored status when reopening the inject', async ({ request, createAtomicTesting, createCredential }) => {
      const awsProduction = await createCredential(`AWS Production ${runId}`, 'CLOUD_AWS');
      const atomicTesting = await createAtomicTesting(`Cloud inject ${runId}`, {
        ...awsConstraint,
        secretReferenceIds: [awsProduction.credential_id],
      });
      const [storedCredential] = await new CredentialApiHelpers(request).findCredentials([awsProduction.credential_id]);

      await openInjectForm(atomicTesting.inject_id);

      const selectedCredential = credentialSection.getSelectedCredential(awsProduction.credential_name);
      await expect(credentialSection.getSelectedCredentials()).toHaveCount(1);
      await expect(selectedCredential).toBeVisible();
      await expect(selectedCredential).toContainText(statusLabel(storedCredential.credential_status));
      // The selection can still be modified
      await expect(credentialSection.updateCredentialButton).toBeEnabled();
      await credentialSection.openPicker();
      await credentialSection.picker.search(runId);
      await expect(credentialSection.picker.getRow(awsProduction.credential_name).getByRole('checkbox')).toBeChecked();
    });

    test('should replace an existing credential association', async ({ request, createAtomicTesting, createCredential }) => {
      const [awsProduction, awsStaging] = await Promise.all([
        createCredential(`AWS Production ${runId}`, 'CLOUD_AWS'),
        createCredential(`AWS Staging ${runId}`, 'CLOUD_AWS'),
      ]);
      const atomicTesting = await createAtomicTesting(`Cloud inject ${runId}`, {
        ...awsConstraint,
        secretReferenceIds: [awsProduction.credential_id],
      });
      await openInjectForm(atomicTesting.inject_id);

      await credentialSection.openPicker();
      await credentialSection.picker.search(runId);
      await expect(credentialSection.picker.getRow(awsProduction.credential_name).getByRole('checkbox')).toBeChecked();
      await credentialSection.picker.select(awsStaging.credential_name);
      // Single selection: the new credential replaces the previous one
      await expect(credentialSection.picker.getRow(awsProduction.credential_name).getByRole('checkbox')).not.toBeChecked();
      await credentialSection.picker.submit();

      await expect(credentialSection.getSelectedCredential(awsStaging.credential_name)).toBeVisible();
      await expect(credentialSection.getSelectedCredential(awsProduction.credential_name)).toHaveCount(0);
      await credentialSection.save();

      const savedInject = await new AtomicTestingApiHelpers(request).getAtomicTesting(atomicTesting.inject_id);
      expect(savedInject.inject_secret_references).toEqual([awsStaging.credential_id]);
    });

    test('should keep the existing association when the modification is cancelled', async ({ request, createAtomicTesting, createCredential }) => {
      const [awsProduction, awsStaging] = await Promise.all([
        createCredential(`AWS Production ${runId}`, 'CLOUD_AWS'),
        createCredential(`AWS Staging ${runId}`, 'CLOUD_AWS'),
      ]);
      const atomicTesting = await createAtomicTesting(`Cloud inject ${runId}`, {
        ...awsConstraint,
        secretReferenceIds: [awsProduction.credential_id],
      });
      await openInjectForm(atomicTesting.inject_id);

      await credentialSection.openPicker();
      await credentialSection.picker.search(runId);
      await credentialSection.picker.select(awsStaging.credential_name);
      await credentialSection.picker.cancel();

      await expect(credentialSection.getSelectedCredential(awsProduction.credential_name)).toBeVisible();
      await expect(credentialSection.getSelectedCredential(awsStaging.credential_name)).toHaveCount(0);
      await credentialSection.save();

      const savedInject = await new AtomicTestingApiHelpers(request).getAtomicTesting(atomicTesting.inject_id);
      expect(savedInject.inject_secret_references).toEqual([awsProduction.credential_id]);
    });

    test('should remove the credential association', async ({ request, createAtomicTesting, createCredential }) => {
      const awsProduction = await createCredential(`AWS Production ${runId}`, 'CLOUD_AWS');
      const atomicTesting = await createAtomicTesting(`Cloud inject ${runId}`, {
        ...awsConstraint,
        secretReferenceIds: [awsProduction.credential_id],
      });
      await openInjectForm(atomicTesting.inject_id);

      await credentialSection.removeCredential(awsProduction.credential_name);

      await expect(credentialSection.getSelectedCredentials()).toHaveCount(0);
      // The field is mandatory: flagged on the form, but it never blocks saving the inject
      await expect(credentialSection.form.getByText('Required', { exact: true })).toBeVisible();
      await credentialSection.save();

      const savedInject = await new AtomicTestingApiHelpers(request).getAtomicTesting(atomicTesting.inject_id);
      expect(savedInject.inject_secret_references).toEqual([]);
    });
  });

  test.describe('Secret confidentiality', () => {
    test('should never display nor return the credential secret to the form', async ({ page, request, createAtomicTesting, createCredential }) => {
      const awsProduction = await createCredential(`AWS Production ${runId}`, 'CLOUD_AWS');
      const atomicTesting = await createAtomicTesting(`Cloud inject ${runId}`, {
        ...awsConstraint,
        secretReferenceIds: [awsProduction.credential_id],
      });
      const [storedCredential] = await new CredentialApiHelpers(request).findCredentials([awsProduction.credential_id]);
      const collectApiResponses = recordApiResponses(page);

      // Only the credential name and its stored status are displayed, in the form...
      await openInjectForm(atomicTesting.inject_id);
      const selectedCredential = credentialSection.getSelectedCredential(awsProduction.credential_name);
      await expect(selectedCredential).toBeVisible();
      await expect(selectedCredential).toContainText(statusLabel(storedCredential.credential_status));
      await expect(credentialSection.form.locator('input[type="password"]')).toHaveCount(0);
      await expectSecretsNotDisplayed(page, awsProduction.secrets);

      // ...and in the selector
      await credentialSection.openPicker();
      await credentialSection.picker.search(runId);
      const pickerRow = credentialSection.picker.getRow(awsProduction.credential_name);
      await expect(pickerRow).toBeVisible();
      await expect(pickerRow).toContainText(statusLabel(storedCredential.credential_status));
      await expectSecretsNotDisplayed(page, awsProduction.secrets);

      // No secret value is returned to the form
      const apiResponses = await collectApiResponses();
      expect(apiResponses).toContain(awsProduction.credential_id);
      awsProduction.secrets.forEach(secret => expect(apiResponses).not.toContain(secret));
    });
  });
});

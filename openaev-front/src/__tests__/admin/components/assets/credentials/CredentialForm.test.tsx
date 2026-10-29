import { TooltipProvider } from '@filigran/design-system';
import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { IntlProvider } from 'react-intl';
import { afterEach, describe, expect, it, vi } from 'vitest';

import DOTS from '../../../../../constants/Strings';
import { type CredentialContractOutput, type CredentialInput } from '../../../../../utils/api-types';
import en from '../../../../../utils/lang/en.json';

const optionalField = (field_name: string, field_type: 'text' | 'password' | 'file' = 'text', field_description?: string) => ({
  field_name,
  field_type,
  required: false,
  field_description,
});

const CERTIFICATE_ACCEPT = '.pem,.crt,.cer,application/x-x509-ca-cert,application/x-pem-file';

const contracts: CredentialContractOutput[] = [
  {
    credential_type: 'CLOUD_AWS',
    credential_auth_method: 'AWS_ACCESS_KEY',
    fields: [
      {
        field_name: 'aws_access_key_id',
        field_type: 'text',
        required: true,
      },
      optionalField('aws_account_id', 'text', 'This value is mandatory for some injectors'),
      optionalField('aws_endpoint_url'),
    ],
  },
  {
    credential_type: 'CLOUD_AZURE',
    credential_auth_method: 'AZURE_SERVICE_PRINCIPAL',
    fields: [
      optionalField('azure_authority_host_uri'),
      optionalField('azure_requests_certificate', 'file'),
    ],
  },
  {
    credential_type: 'CLOUD_GCP',
    credential_auth_method: 'GCP_OAUTH2',
    fields: [
      optionalField('gcp_custom_token_uri'),
      optionalField('gcp_custom_oauth_access_token', 'password'),
    ],
  },
];

vi.mock('../../../../../actions/assets/credential-actions', () => ({ fetchCredentialContracts: vi.fn(() => Promise.resolve({ data: contracts })) }));
// The tag field reads the tags store, unrelated to the contract-driven fields under test.
vi.mock('../../../../../components/fields/TagFieldController', () => ({ default: () => null }));

// Imported after the mocks so the component picks them up.
const { default: CredentialForm } = await import('../../../../../admin/components/assets/credentials/CredentialForm');

const theme = createTheme();

const renderForm = (initialValues: Partial<CredentialInput> & Record<string, unknown>, editing = false) => {
  const onSubmit = vi.fn<(formData: FormData) => void>();
  render(
    <ThemeProvider theme={theme}>
      <TooltipProvider>
        <IntlProvider locale="en" defaultLocale="en" messages={en} onError={() => {}}>
          <CredentialForm
            onSubmit={onSubmit}
            handleClose={vi.fn()}
            editing={editing}
            initialValues={{
              credential_name: 'cloud credential',
              ...initialValues,
            }}
          />
        </IntlProvider>
      </TooltipProvider>
    </ThemeProvider>,
  );
  return onSubmit;
};

const submittedInput = async (formData: FormData): Promise<Record<string, unknown>> => JSON.parse(await (formData.get('input') as Blob).text());

const submit = () => fireEvent.click(screen.getByRole('button', { name: /create|update/i }));

describe('CredentialForm', () => {
  // No vi.clearAllMocks() here: it would drop the contracts mock implementation for the next tests.
  afterEach(() => {
    cleanup();
  });

  describe('sovereign cloud fields', () => {
    it('shows the AWS fields and the account id hint only for an AWS auth method', async () => {
      // Arrange
      renderForm({
        credential_type: 'CLOUD_AWS',
        credential_auth_method: 'AWS_ACCESS_KEY',
      });

      // Assert
      expect(await screen.findByLabelText('Account ID')).toBeTruthy();
      expect(screen.getByLabelText('Endpoint URL')).toBeTruthy();
      expect(screen.getByText('This value is mandatory for some injectors')).toBeTruthy();
      expect(screen.queryByText('Authority host URI')).toBeNull();
      expect(screen.queryByText('Custom token URI')).toBeNull();
    });

    it('shows the Azure fields only for an Azure auth method', async () => {
      // Arrange
      renderForm({
        credential_type: 'CLOUD_AZURE',
        credential_auth_method: 'AZURE_SERVICE_PRINCIPAL',
      });

      // Assert
      expect(await screen.findByLabelText('Authority host URI')).toBeTruthy();
      expect(screen.getByText('Requests Certificate')).toBeTruthy();
      expect(screen.queryByText('Account ID')).toBeNull();
      expect(screen.queryByText('Custom token URI')).toBeNull();
    });

    it('submits without any of the new optional fields', async () => {
      // Arrange
      const onSubmit = renderForm({
        credential_type: 'CLOUD_AWS',
        credential_auth_method: 'AWS_ACCESS_KEY',
      });
      fireEvent.change(await screen.findByLabelText(/Access key id/), { target: { value: 'AKIA' } });

      // Act
      submit();

      // Assert
      await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
      const input = await submittedInput(onSubmit.mock.calls[0][0]);
      expect(input.aws_access_key_id).toBe('AKIA');
      expect(input.aws_account_id ?? '').toBe('');
      expect(input.aws_endpoint_url ?? '').toBe('');
    });
  });

  describe('edit mode', () => {
    it('masks the stored OAuth access token and leaves it out of an untouched submit', async () => {
      // Arrange
      const onSubmit = renderForm({
        credential_type: 'CLOUD_GCP',
        credential_auth_method: 'GCP_OAUTH2',
        gcp_custom_oauth_access_token: DOTS,
      }, true);
      const tokenInput = await screen.findByLabelText('Custom OAuth access token') as HTMLInputElement;
      expect(tokenInput.value).toBe(DOTS);
      fireEvent.change(screen.getByLabelText(/Custom token URI/), { target: { value: 'https://oauth2.mock.local/token' } });

      // Act
      submit();

      // Assert
      await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
      const input = await submittedInput(onSubmit.mock.calls[0][0]);
      expect(input.gcp_custom_token_uri).toBe('https://oauth2.mock.local/token');
      expect(input).not.toHaveProperty('gcp_custom_oauth_access_token');
    });

    it('keeps the stored certificate when no new file is picked', async () => {
      // Arrange
      const onSubmit = renderForm({
        credential_type: 'CLOUD_AZURE',
        credential_auth_method: 'AZURE_SERVICE_PRINCIPAL',
        azure_requests_certificate: DOTS,
      }, true);
      fireEvent.change(await screen.findByLabelText(/Authority host URI/), { target: { value: 'https://login.mock.local/' } });

      // Act
      submit();

      // Assert
      await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
      const formData = onSubmit.mock.calls[0][0];
      expect(formData.has('azure_requests_certificate')).toBe(false);
      expect(await submittedInput(formData)).not.toHaveProperty('azure_requests_certificate');
    });
  });

  describe('requests certificate', () => {
    it('only accepts certificate files and sends the picked one as its own part', async () => {
      // Arrange
      const onSubmit = renderForm({
        credential_type: 'CLOUD_AZURE',
        credential_auth_method: 'AZURE_SERVICE_PRINCIPAL',
      });
      await screen.findByText('Requests Certificate');
      const fileInput = document.querySelector('input[type="file"]') as HTMLInputElement;
      const certificate = new File(['-----BEGIN CERTIFICATE-----'], 'ca.pem', { type: '' });

      // Act
      fireEvent.change(fileInput, { target: { files: [certificate] } });
      submit();

      // Assert
      expect(fileInput.getAttribute('accept')).toBe(CERTIFICATE_ACCEPT);
      await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
      expect(onSubmit.mock.calls[0][0].get('azure_requests_certificate')).toBeInstanceOf(File);
    });

    it('rejects a file that is not a certificate', async () => {
      // Arrange
      const onSubmit = renderForm({
        credential_type: 'CLOUD_AZURE',
        credential_auth_method: 'AZURE_SERVICE_PRINCIPAL',
      });
      await screen.findByText('Requests Certificate');
      fireEvent.change(screen.getByLabelText(/Authority host URI/), { target: { value: 'https://login.mock.local/' } });
      const fileInput = document.querySelector('input[type="file"]') as HTMLInputElement;
      const notACertificate = new File(['{}'], 'key.json', { type: 'application/json' });

      // Act
      fireEvent.change(fileInput, { target: { files: [notACertificate] } });
      submit();

      // Assert
      await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
      expect(onSubmit.mock.calls[0][0].has('azure_requests_certificate')).toBe(false);
    });
  });
});

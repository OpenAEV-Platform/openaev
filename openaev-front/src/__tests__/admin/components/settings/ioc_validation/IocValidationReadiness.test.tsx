import { cleanup, render, screen, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { IOC_VALIDATION_DOCUMENTATION_URL, IocValidationReadiness } from '../../../../../admin/components/settings/ioc_validation/IocValidationSettings';
import { type AppAbility } from '../../../../../utils/permissions/ability';
import { AbilityProvider } from '../../../../../utils/permissions/permissionsContext';

vi.mock('../../../../../components/i18n', async (importOriginal) => {
  const original = await importOriginal();
  return {
    ...(original as Record<string, unknown>),
    useFormatter: () => ({ t: (value: string) => value }),
  };
});

vi.mock('../../../../../actions/ioc_validations/ioc-validation-actions', () => ({
  fetchIocValidationSettings: vi.fn(),
  updateIocValidationSettings: vi.fn(),
  searchIocValidationAssetGroupOptions: vi.fn(),
}));

const renderReadiness = (openctiEnabled: boolean, connectorRegistered: boolean, canConfigurePlatform = true) => render(
  <AbilityProvider value={{
    can: () => canConfigurePlatform,
    on: () => () => {},
  } as unknown as AppAbility}
  >
    <IocValidationReadiness openctiEnabled={openctiEnabled} connectorRegistered={connectorRegistered} />
  </AbilityProvider>,
);

describe('IocValidationReadiness', () => {
  afterEach(() => {
    cleanup();
  });

  it('points the platform administrator to the OpenCTI connection configuration', () => {
    renderReadiness(false, false);

    const alert = screen.getByTestId('ioc-validation-opencti-missing');
    expect(within(alert).getByText('Add the OpenCTI connection of this tenant to the platform configuration (XTM Suite connector), then restart OpenAEV.')).toBeTruthy();
    expect(within(alert).getByText('Configure the OpenCTI connection').closest('a')?.getAttribute('href'))
      .toBe('https://docs.openaev.io/latest/usage/evaluate/xtm-suite-connector/#step-1-configure-openaev-to-connect-to-opencti');
    expect(screen.queryByTestId('ioc-validation-connector-unregistered')).toBeNull();
  });

  it('asks the administrator when the reader cannot change the platform configuration', () => {
    renderReadiness(false, false, false);

    const alert = screen.getByTestId('ioc-validation-opencti-missing');
    expect(within(alert).getByText('Ask your administrator to configure the OpenCTI connection of this tenant.')).toBeTruthy();
    expect(within(alert).queryByText('Configure the OpenCTI connection')).toBeNull();
  });

  it('says how the connector gets registered and what its OpenCTI account needs', () => {
    renderReadiness(true, false);

    const alert = screen.getByTestId('ioc-validation-connector-unregistered');
    expect(within(alert).getByText(/give that account the Connector role/)).toBeTruthy();
    expect(within(alert).getByText('How to configure IOC validation').closest('a')?.getAttribute('href'))
      .toBe(`${IOC_VALIDATION_DOCUMENTATION_URL}#configure-ioc-validation`);
  });

  it('shows nothing once OpenCTI is connected and the connector registered', () => {
    const { container } = renderReadiness(true, true);

    expect(container.textContent).toBe('');
  });
});

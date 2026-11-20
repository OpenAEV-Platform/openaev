import '@testing-library/jest-dom/vitest';

import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import PayloadUpdateDialog from '../../../../../admin/components/threat_arsenal/customization/PayloadUpdateDialog';
import { type PayloadCustomizationUpdateOutput } from '../../../../../utils/api-types';
import { expectNoMuiControls } from '../../../../utils/designSystemAssertions';

const mocks = vi.hoisted(() => ({ updateCustomization: vi.fn() }));

vi.mock('../../../../../components/i18n', () => ({ useFormatter: () => ({ t: (message: string, values?: Record<string, string>) => message.replace(/\{(\w+)\}/g, (_, key: string) => values?.[key] ?? key) }) }));
vi.mock('../../../../../actions/threat_arsenals/threatArsenal-actions', () => ({ updateThreatArsenalCustomization: mocks.updateCustomization }));

const update: PayloadCustomizationUpdateOutput = {
  update_payload_id: 'filigran-1.1.0',
  update_version: '1.1.0',
  update_fields: [
    {
      field_name: 'COMMAND_CONTENT',
      field_base: 'whoami',
      field_filigran: 'whoami /all',
      field_yours: 'whoami /priv',
      field_conflict: true,
    },
    {
      field_name: 'CLEANUP_COMMAND',
      field_base: 'del out.txt',
      field_filigran: 'del /f out.txt',
      field_yours: 'del out.txt',
      field_conflict: false,
    },
    {
      field_name: 'PLATFORMS',
      field_base: ['Windows'],
      field_filigran: ['Windows'],
      field_yours: ['Linux', 'Windows'],
      field_conflict: false,
    },
  ],
  update_other_changes: ['payload_description'],
};

const renderDialog = () => {
  const onUpdated = vi.fn();
  render(<PayloadUpdateDialog actionId="copy-action" update={update} onClose={vi.fn()} onUpdated={onUpdated} />);
  return onUpdated;
};

const updateButton = () => screen.getByRole('button', { name: 'Update' });

describe('PayloadUpdateDialog', () => {
  beforeEach(() => mocks.updateCustomization.mockReset());
  afterEach(cleanup);

  it('given_aConflict_should_askForAChoiceBeforeUpdating', () => {
    // Act
    renderDialog();

    // Assert
    const dialog = screen.getByRole('dialog');
    expect(dialog).toHaveAccessibleName('Update to Filigran v1.1.0');
    expect(screen.getByRole('radiogroup', { name: 'Command' })).toBeInTheDocument();
    expect(screen.getByRole('radio', { name: 'Filigran v1.1.0' })).toHaveAttribute('aria-checked', 'false');
    expect(screen.getByRole('radio', { name: 'Yours' })).toHaveAttribute('aria-checked', 'false');
    expect(updateButton()).toBeDisabled();
    expectNoMuiControls(dialog, 'Update dialog');
  });

  it('given_anUpdate_should_listWhatIsNewAndWhatIsKept', () => {
    // Act
    renderDialog();

    // Assert
    expect(screen.getByText('Cleanup command')).toBeInTheDocument();
    expect(screen.getByText('del /f out.txt')).toBeInTheDocument();
    expect(screen.getByText('Also changed: Description')).toBeInTheDocument();
    expect(screen.getByText('Platforms')).toBeInTheDocument();
  });

  it('given_aChoiceOnEveryConflict_should_updateWithTheChoices', async () => {
    // Arrange
    const saved = { injector_contract_id: 'copy-action-1.1.0' };
    mocks.updateCustomization.mockResolvedValue({ data: saved });
    const onUpdated = renderDialog();
    fireEvent.click(screen.getByRole('radio', { name: 'Yours' }));

    // Act
    fireEvent.click(updateButton());

    // Assert
    await waitFor(() => expect(onUpdated).toHaveBeenCalledWith(saved));
    expect(mocks.updateCustomization).toHaveBeenCalledWith('copy-action', {
      update_payload_id: 'filigran-1.1.0',
      update_choices: { COMMAND_CONTENT: 'YOURS' },
    });
  });
});

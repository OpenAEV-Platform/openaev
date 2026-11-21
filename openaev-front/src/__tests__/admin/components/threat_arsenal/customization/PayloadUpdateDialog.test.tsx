import '@testing-library/jest-dom/vitest';

import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import PayloadUpdateDialog from '../../../../../admin/components/threat_arsenal/customization/PayloadUpdateDialog';
import { type PayloadCustomizationUpdateOutput } from '../../../../../utils/api-types';
import { MESSAGING$ } from '../../../../../utils/Environment';
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
      field_block: 'EXECUTION',
      field_base: 'whoami',
      field_filigran: 'whoami /all',
      field_yours: 'whoami /priv',
      field_status: 'CONFLICT',
    },
    {
      field_name: 'CLEANUP_COMMAND',
      field_block: 'EXECUTION',
      field_base: 'del out.txt',
      field_filigran: 'del /f out.txt',
      field_yours: 'del out.txt',
      field_status: 'CONFLICT',
    },
    {
      field_name: 'PLATFORMS',
      field_block: 'PLATFORMS',
      field_base: ['Windows'],
      field_filigran: ['Windows', 'Linux'],
      field_yours: ['Windows'],
      field_status: 'TAKE_APPLIED',
    },
    {
      field_name: 'ARGUMENTS',
      field_block: 'EXECUTION',
      field_base: [],
      field_filigran: [],
      field_yours: [{
        key: 'user',
        default_value: 'admin',
      }],
      field_status: 'KEEP_MINE',
    },
  ],
  update_other_changes: ['payload_description'],
  update_release_notes: [{
    release_version: '1.1.0',
    release_changelog: 'Lists every privilege.',
  }],
};

const renderDialog = () => {
  const onUpdated = vi.fn();
  render(<PayloadUpdateDialog actionId="copy-action" baseVersion="1.0.0" update={update} onClose={vi.fn()} onUpdated={onUpdated} />);
  return onUpdated;
};

const updateButton = () => screen.getByRole('button', { name: 'Update' });

describe('PayloadUpdateDialog', () => {
  beforeEach(() => {
    mocks.updateCustomization.mockReset();
  });
  afterEach(cleanup);

  it('given_aConflictingBlock_should_askForOneChoiceOnAllItsFields', () => {
    // Act
    renderDialog();

    // Assert
    const dialog = screen.getByRole('dialog');
    expect(dialog).toHaveAccessibleName('Update to Filigran v1.1.0');
    expect(screen.getAllByRole('radiogroup')).toHaveLength(1);
    const block = screen.getByRole('radiogroup', { name: 'Command, Cleanup command' });
    expect(block).toHaveTextContent('del /f out.txt');
    expect(block).toHaveTextContent('Cleanup command');
    expect(screen.getByRole('radio', { name: 'Filigran version v1.1.0' })).toHaveAttribute('aria-checked', 'false');
    expect(screen.getByRole('radio', { name: 'Yours' })).toHaveAttribute('aria-checked', 'false');
    expect(updateButton()).toBeDisabled();
    expectNoMuiControls(dialog, 'Update dialog');
  });

  it('given_anUpdate_should_showWhatIsNewAsADiff', () => {
    // Act
    renderDialog();

    // Assert
    const changes = screen.getByRole('group', { name: 'Changes' });
    expect(within(changes).getByText('Filigran version v1.0.0')).toBeInTheDocument();
    expect(within(changes).getByRole('group', { name: 'Platforms · 1 lines added, 0 lines removed' })).toBeInTheDocument();
    expect(screen.getByText('Also changed: Description')).toBeInTheDocument();
    expect(screen.getByText('v1.1.0')).toBeInTheDocument();
    expect(screen.getByText('Lists every privilege.')).toBeInTheDocument();
    expect(screen.getByText('Your edits — the update keeps them')).toBeInTheDocument();
    expect(screen.getByText('Arguments')).toBeInTheDocument();
  });

  it('given_aChoiceOnEveryConflict_should_updateWithTheChoices', async () => {
    // Arrange
    const saved = { injector_contract_id: 'copy-action-1.1.0' };
    mocks.updateCustomization.mockResolvedValue({ data: saved });
    const notifySuccess = vi.spyOn(MESSAGING$, 'notifySuccess');
    const onUpdated = renderDialog();
    fireEvent.click(screen.getByRole('radio', { name: 'Yours' }));

    // Act
    fireEvent.click(updateButton());

    // Assert
    await waitFor(() => expect(onUpdated).toHaveBeenCalledWith(saved));
    expect(notifySuccess).toHaveBeenCalledWith('Updated to Filigran v1.1.0');
    expect(mocks.updateCustomization).toHaveBeenCalledWith('copy-action', {
      update_payload_id: 'filigran-1.1.0',
      update_choices: { EXECUTION: 'KEEP_MINE' },
    });
  });

  it('given_theUpdateRefused_should_stayOpenWithoutUpdating', async () => {
    // Arrange
    mocks.updateCustomization.mockRejectedValue(new Error('refused'));
    const onUpdated = renderDialog();
    fireEvent.click(screen.getByRole('radio', { name: 'Filigran version v1.1.0' }));

    // Act
    fireEvent.click(updateButton());

    // Assert
    await waitFor(() => expect(updateButton()).toBeEnabled());
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(onUpdated).not.toHaveBeenCalled();
  });
});

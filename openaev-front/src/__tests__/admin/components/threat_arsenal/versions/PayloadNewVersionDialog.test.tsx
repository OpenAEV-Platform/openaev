import '@testing-library/jest-dom/vitest';

import { cleanup, fireEvent, render, renderHook, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import PayloadNewVersionDialog from '../../../../../admin/components/threat_arsenal/versions/PayloadNewVersionDialog';
import useSkipNewVersionDialog from '../../../../../admin/components/threat_arsenal/versions/useSkipNewVersionDialog';
import { expectNoMuiControls } from '../../../../utils/designSystemAssertions';
import { actionId, olderVersion, payloadId, usage, versionAction } from './payloadVersionFixtures';

const mocks = vi.hoisted(() => ({
  moveUsages: vi.fn(),
  notifyMoves: vi.fn(),
}));

vi.mock('../../../../../components/i18n', () => ({ useFormatter: () => ({ t: (message: string, values?: Record<string, string>) => message.replace(/\{(\w+)\}/g, (_, key: string) => values?.[key] ?? key) }) }));
vi.mock('../../../../../admin/components/threat_arsenal/versions/usageMove', () => ({ moveUsages: mocks.moveUsages }));
vi.mock('../../../../../admin/components/threat_arsenal/versions/usages/useNotifyMoves', () => ({ default: () => mocks.notifyMoves }));

const firstVersion = olderVersion('1.0.0', usage('inject-1', 'scenario-1'), usage('inject-2', 'scenario-1'));
const secondVersion = olderVersion('1.1.0', usage('inject-3', 'scenario-2'));

const renderDialog = () => {
  const onClose = vi.fn();
  render(
    <MemoryRouter>
      <PayloadNewVersionDialog action={versionAction('1.2.0')} olderVersions={[firstVersion, secondVersion]} onClose={onClose} />
    </MemoryRouter>,
  );
  return onClose;
};

const moveButton = () => screen.getByRole('button', { name: /^Move/ });

describe('PayloadNewVersionDialog', () => {
  beforeEach(() => {
    localStorage.clear();
    mocks.moveUsages.mockReset();
    mocks.notifyMoves.mockReset();
  });
  afterEach(cleanup);

  it('given_olderVersionsInUse_should_listEachOneWithNothingSelected', () => {
    // Act
    renderDialog();

    // Assert
    const dialog = screen.getByRole('dialog');
    expect(dialog).toHaveAccessibleName('New version v1.2.0');
    expect(screen.getByText('3 injects still use older versions.')).toBeInTheDocument();
    expect(screen.getByText('(2 injects in 1 scenario)')).toBeInTheDocument();
    expect(screen.getByText('(1 inject in 1 scenario)')).toBeInTheDocument();
    expect(screen.getByRole('checkbox', { name: 'Every inject on v1.0.0' })).toHaveAttribute('aria-checked', 'false');
    expect(moveButton()).toHaveAccessibleName('Move to v1.2.0');
    expect(moveButton()).toBeDisabled();
    expect(screen.getByRole('link', { name: 'Choose in the Versions tab' }))
      .toHaveAttribute('href', `/admin/threat-arsenal?action=${actionId('1.2.0')}&tab=versions&focus=move`);
    expectNoMuiControls(dialog, 'New version dialog');
  });

  it('given_everyInjectSelected_should_moveThemAndClose', async () => {
    // Arrange
    mocks.moveUsages.mockResolvedValue({
      moved_ids: [],
      refused: [],
    });
    const onClose = renderDialog();
    fireEvent.click(screen.getByRole('button', { name: 'Select all 3' }));

    // Act
    fireEvent.click(moveButton());

    // Assert
    await waitFor(() => expect(onClose).toHaveBeenCalled());
    expect(mocks.moveUsages).toHaveBeenCalledWith(actionId('1.0.0'), payloadId('1.2.0'), firstVersion.usages.payload_usages);
    expect(mocks.moveUsages).toHaveBeenCalledWith(actionId('1.1.0'), payloadId('1.2.0'), secondVersion.usages.payload_usages);
  });

  it('given_oneVersionChecked_should_offerToMoveItsInjects', () => {
    // Arrange
    renderDialog();

    // Act
    fireEvent.click(screen.getByRole('checkbox', { name: 'Every inject on v1.1.0' }));

    // Assert
    expect(screen.getByRole('checkbox', { name: 'Every inject on v1.1.0' })).toHaveAttribute('aria-checked', 'true');
    expect(screen.getByText('1 of 3 selected')).toBeInTheDocument();
    expect(moveButton()).toHaveAccessibleName('Move 1 to v1.2.0');
    expect(moveButton()).toBeEnabled();
  });

  it('given_later_should_closeWithoutMoving', () => {
    // Arrange
    const onClose = renderDialog();

    // Act
    fireEvent.click(screen.getByRole('button', { name: 'Later' }));

    // Assert
    expect(onClose).toHaveBeenCalled();
    expect(mocks.moveUsages).not.toHaveBeenCalled();
  });

  it('given_dontAskAgainChecked_should_rememberIt', () => {
    // Arrange
    renderDialog();

    // Act
    fireEvent.click(screen.getByRole('checkbox', { name: 'Don\'t ask me again' }));

    // Assert
    expect(renderHook(() => useSkipNewVersionDialog()).result.current[0]).toBe(true);
  });
});

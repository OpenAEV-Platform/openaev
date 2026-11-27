import '@testing-library/jest-dom/vitest';

import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';

import FiligranValuesContext from '../../../../../admin/components/threat_arsenal/customization/FiligranValuesContext';
import LockedFields from '../../../../../admin/components/threat_arsenal/customization/LockedFields';

const renderLocked = (customized: boolean) => render(
  <FiligranValuesContext.Provider value={customized ? { action_name: 'Mimikatz' } : null}>
    <LockedFields><input aria-label="Description" /></LockedFields>
    <input aria-label="Command" />
  </FiligranValuesContext.Provider>,
);

describe('LockedFields', () => {
  afterEach(cleanup);

  it('given_aCustomizedCopy_should_lockOnlyTheFieldsItWraps', () => {
    // Act
    renderLocked(true);

    // Assert
    expect(screen.getByRole('textbox', { name: 'Description' })).toBeDisabled();
    expect(screen.getByRole('textbox', { name: 'Command' })).toBeEnabled();
  });

  it('given_anyOtherAction_should_leaveTheFieldsEditable', () => {
    // Act
    renderLocked(false);

    // Assert
    expect(screen.getByRole('textbox', { name: 'Description' })).toBeEnabled();
  });
});

import '@testing-library/jest-dom/vitest';

import { act, cleanup, fireEvent, render, renderHook, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import AskAfterSavingChoice from '../../../../../admin/components/threat_arsenal/versions/AskAfterSavingChoice';
import useSkipNewVersionDialog from '../../../../../admin/components/threat_arsenal/versions/useSkipNewVersionDialog';

vi.mock('../../../../../components/i18n', () => ({ useFormatter: () => ({ t: (message: string) => message }) }));

describe('AskAfterSavingChoice', () => {
  beforeEach(() => localStorage.clear());
  afterEach(cleanup);

  it('given the dialog still asked should show nothing', () => {
    // Act
    render(<AskAfterSavingChoice />);

    // Assert
    expect(screen.queryByRole('button', { name: 'Ask me again' })).toBeNull();
  });

  it('given ask me again clicked should ask again after the next save', () => {
    // Arrange
    const skip = renderHook(() => useSkipNewVersionDialog());
    act(() => skip.result.current[1](true));
    render(<AskAfterSavingChoice />);
    expect(screen.getByText('After saving a new version, you are not asked to move injects.')).toBeInTheDocument();

    // Act
    fireEvent.click(screen.getByRole('button', { name: 'Ask me again' }));

    // Assert
    expect(screen.queryByRole('button', { name: 'Ask me again' })).toBeNull();
    expect(renderHook(() => useSkipNewVersionDialog()).result.current[0]).toBe(false);
  });
});

import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { type ReactNode } from 'react';
import { IntlProvider } from 'react-intl';
import { afterEach, describe, expect, it, vi } from 'vitest';

import FindingTriageControl from '../../../../admin/components/findings/FindingTriageControl';
import { type AggregatedFindingOutput } from '../../../../utils/api-types';

type TriageStatus = NonNullable<AggregatedFindingOutput['finding_triage_status']>;

const { mockUpdateFindingTriage, mockMe } = vi.hoisted(() => ({
  mockUpdateFindingTriage: vi.fn(() => Promise.resolve({ data: {} })),
  // Mutable so each test can flip the admin flag without re-mocking the module.
  mockMe: { user_admin: false as boolean },
}));

vi.mock('../../../../actions/findings/finding-triage-actions', () => ({ updateFindingTriage: mockUpdateFindingTriage }));

vi.mock('../../../../utils/hooks/useAuth', () => ({ default: () => ({ me: mockMe }) }));

vi.mock('../../../../components/i18n', async (importOriginal) => {
  const original = await importOriginal();
  return {
    ...(original as Record<string, unknown>),
    useFormatter: () => ({ t: (value: string) => value }),
  };
});

const theme = createTheme();

const wrapper = ({ children }: { children: ReactNode }) => (
  <ThemeProvider theme={theme}>
    <IntlProvider locale="en" defaultLocale="en" onError={() => {}}>
      {children}
    </IntlProvider>
  </ThemeProvider>
);

const renderControl = (status: TriageStatus, onStatusChange = vi.fn()) => {
  render(
    <FindingTriageControl
      findingId="finding-1"
      status={status}
      onStatusChange={onStatusChange}
    />,
    { wrapper },
  );
  return { onStatusChange };
};

// The chip + dropdown-arrow trigger is a <span> wrapping the ItemStatus chip; clicking the
// chip's own label bubbles up to that span, which is what opens the menu (see
// FindingTriageControl's onClick handler).
const openMenuFor = (status: TriageStatus) => fireEvent.click(screen.getByText(status, { selector: '.MuiChip-label' }).closest('span')!);

describe('FindingTriageControl', () => {
  afterEach(() => {
    cleanup();
    mockMe.user_admin = false;
    vi.clearAllMocks();
  });

  describe('Available transitions', () => {
    it('offers only CONFIRMED and FALSE_POSITIVE from UNTRIAGED for a non-admin', () => {
      // Arrange / Act
      renderControl('UNTRIAGED');
      openMenuFor('UNTRIAGED');

      // Assert
      const menu = screen.getByRole('menu');
      expect(within(menu).getByText('CONFIRMED')).toBeDefined();
      expect(within(menu).getByText('FALSE_POSITIVE')).toBeDefined();
      expect(within(menu).queryByText('Revert to Untriaged')).toBeNull();
    });

    it('offers only RISK_ACCEPTED and FALSE_POSITIVE from CONFIRMED', () => {
      // Arrange / Act
      renderControl('CONFIRMED');
      openMenuFor('CONFIRMED');

      // Assert
      const menu = screen.getByRole('menu');
      expect(within(menu).getByText('RISK_ACCEPTED')).toBeDefined();
      expect(within(menu).getByText('FALSE_POSITIVE')).toBeDefined();
    });

    it.each(['FALSE_POSITIVE', 'RISK_ACCEPTED'] as TriageStatus[])('renders %s as a non-interactive chip for a non-admin (terminal status)', (status) => {
      // Arrange
      renderControl(status);

      // Act
      openMenuFor(status);

      // Assert: no options, so no menu ever opens
      expect(screen.queryByRole('menu')).toBeNull();
    });
  });

  describe('Admin-only revert', () => {
    it('does not show "Revert to Untriaged" for a non-admin on a non-UNTRIAGED status', () => {
      // Arrange
      mockMe.user_admin = false;

      // Act
      renderControl('CONFIRMED');
      openMenuFor('CONFIRMED');

      // Assert
      expect(screen.queryByText('Revert to Untriaged')).toBeNull();
    });

    it('shows "Revert to Untriaged" for an admin on a non-UNTRIAGED status', () => {
      // Arrange
      mockMe.user_admin = true;

      // Act
      renderControl('CONFIRMED');
      openMenuFor('CONFIRMED');

      // Assert
      expect(screen.getByText('Revert to Untriaged')).toBeDefined();
    });

    it('never shows "Revert to Untriaged" while already UNTRIAGED, even for an admin', () => {
      // Arrange
      mockMe.user_admin = true;

      // Act
      renderControl('UNTRIAGED');
      openMenuFor('UNTRIAGED');

      // Assert
      expect(screen.queryByText('Revert to Untriaged')).toBeNull();
    });
  });

  describe('Justification dialog', () => {
    it('disables Confirm until the justification reaches the 10-character minimum', () => {
      // Arrange
      renderControl('UNTRIAGED');
      openMenuFor('UNTRIAGED');
      fireEvent.click(screen.getByText('CONFIRMED'));

      // Act
      const textarea = screen.getByLabelText('Justification');
      fireEvent.change(textarea, { target: { value: 'too short' } });

      // Assert
      expect((screen.getByText('Confirm').closest('button') as HTMLButtonElement).disabled).toBe(true);

      // Act again: cross the 10-character minimum
      fireEvent.change(textarea, { target: { value: 'this is long enough' } });

      // Assert
      expect((screen.getByText('Confirm').closest('button') as HTMLButtonElement).disabled).toBe(false);
    });

    it('calls updateFindingTriage with the target status and justification, then onStatusChange on success', async () => {
      // Arrange
      const { onStatusChange } = renderControl('UNTRIAGED');
      openMenuFor('UNTRIAGED');
      fireEvent.click(screen.getByText('CONFIRMED'));
      fireEvent.change(screen.getByLabelText('Justification'), { target: { value: 'confirmed after review' } });

      // Act
      fireEvent.click(screen.getByText('Confirm').closest('button')!);

      // Assert
      await waitFor(() => expect(mockUpdateFindingTriage).toHaveBeenCalledWith('finding-1', 'CONFIRMED', 'confirmed after review'));
      await waitFor(() => expect(onStatusChange).toHaveBeenCalledWith('CONFIRMED'));
    });
  });
});

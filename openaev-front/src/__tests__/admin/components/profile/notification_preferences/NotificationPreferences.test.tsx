import { TooltipProvider } from '@filigran/design-system';
import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const { mockFetch, mockUpdate } = vi.hoisted(() => ({
  mockFetch: vi.fn(),
  mockUpdate: vi.fn(),
}));

vi.mock('../../../../../actions/notifications/notification-preference-actions', () => ({
  fetchMyNotificationPreferences: () => mockFetch(),
  updateMyNotificationPreferences: (...args: unknown[]) => mockUpdate(...args),
}));

vi.mock('../../../../../components/i18n', () => ({ useFormatter: () => ({ t: (value: string) => value }) }));

vi.mock('../../../../../components/Breadcrumbs', () => ({ default: () => null }));

const { default: NotificationPreferences } = await import('../../../../../admin/components/profile/notification_preferences/NotificationPreferences');

const DEFAULTS = [
  {
    notification_preference_type: 'PAYLOAD_APPROVAL_REQUESTS',
    notification_preference_in_app: true,
    notification_preference_email: false,
  },
  {
    notification_preference_type: 'PAYLOAD_APPROVAL_OUTCOMES',
    notification_preference_in_app: true,
    notification_preference_email: false,
  },
];

const renderPage = () => render(
  <MemoryRouter>
    <ThemeProvider theme={createTheme()}>
      <TooltipProvider>
        <NotificationPreferences />
      </TooltipProvider>
    </ThemeProvider>
  </MemoryRouter>,
);

describe('NotificationPreferences', () => {
  beforeEach(() => {
    mockFetch.mockReset();
    mockUpdate.mockReset();
    mockFetch.mockResolvedValue({ data: DEFAULTS });
    mockUpdate.mockResolvedValue({ data: DEFAULTS });
  });

  afterEach(() => {
    cleanup();
  });

  it('shows each type with in-app on and email off by default', async () => {
    // Arrange / Act
    renderPage();

    // Assert
    expect(await screen.findByText('Payloads to approve')).toBeTruthy();
    expect(screen.getByText('Outcome of my payloads')).toBeTruthy();
    await waitFor(() => expect(screen.getAllByRole('switch', { name: 'In-app' })[0].getAttribute('aria-checked')).toBe('true'));
    expect(screen.getAllByRole('switch', { name: 'Email' }).map(s => s.getAttribute('aria-checked'))).toEqual(['false', 'false']);
    expect(screen.getByRole('tab', { name: 'Preferences' }).getAttribute('aria-selected')).toBe('true');
  });

  it('turns email on for one type, keeping its in-app choice', async () => {
    // Arrange
    renderPage();
    await waitFor(() => expect(screen.getAllByRole('switch', { name: 'Email' })[0].hasAttribute('disabled')).toBe(false));

    // Act
    fireEvent.click(screen.getAllByRole('switch', { name: 'Email' })[0]);

    // Assert
    await waitFor(() => expect(mockUpdate).toHaveBeenCalledWith([{
      notification_preference_type: 'PAYLOAD_APPROVAL_REQUESTS',
      notification_preference_in_app: true,
      notification_preference_email: true,
    }]));
  });
});

import { TooltipProvider } from '@filigran/design-system';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import { IntlProvider } from 'react-intl';
import { MemoryRouter } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { expectNoMuiControls } from '../../../utils/designSystemAssertions';

// The bell's data path is not what this file is about: it covers the unread
// marker being a library Badge rather than a MUI one.
let unread = 0;
vi.mock('../../../../actions/notifications/notification-actions', () => ({ getUnreadNotificationsCount: () => Promise.resolve({ data: unread }) }));
vi.mock('../../../../store', () => ({ useHelper: () => [] }));

const { default: TopBarNotifications } = await import('../../../../admin/components/nav/TopBarNotifications');

const renderBell = () => render(
  <MemoryRouter>
    <IntlProvider locale="en" messages={{}}>
      <TooltipProvider>
        <TopBarNotifications />
      </TooltipProvider>
    </IntlProvider>
  </MemoryRouter>,
);

describe('TopBarNotifications unread marker', () => {
  afterEach(() => {
    cleanup();
    unread = 0;
  });

  it('marks unread notifications with the library Badge, not a MUI one', async () => {
    // Sandy's rule: an implemented component is composed of library components
    // only. This was `<Badge variant="dot">` from MUI, kept as compensation #22
    // until the library shipped a Badge (#114).
    unread = 3;
    const { container } = renderBell();
    await waitFor(() => expect(container.querySelector('[data-testid="badge"], span[class*="bg-"]')).not.toBeNull());
    expectNoMuiControls(container, 'the notifications bell');
  });

  it('announces the count even though it renders as a dot', async () => {
    // `dot` shows "there is something" without the number; the value must still
    // reach assistive technology, which is what the library's Badge guarantees.
    unread = 7;
    renderBell();
    await waitFor(() => expect(screen.getByRole('link', { name: 'notifications' })).toBeDefined());
    await waitFor(() => expect(document.body.textContent).toContain('7'));
  });

  it('renders no marker when everything is read', async () => {
    unread = 0;
    const { container } = renderBell();
    await waitFor(() => expect(screen.getByRole('link', { name: 'notifications' })).toBeDefined());
    expect(container.textContent).not.toContain('0');
    expectNoMuiControls(container, 'the notifications bell with nothing unread');
  });
});

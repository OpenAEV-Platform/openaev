// `@testing-library/jest-dom` provides `toHaveAccessibleName` /
// `toHaveAccessibleDescription`, which compute what a screen reader is handed —
// following aria-describedby and honouring aria-hidden. Imported here rather than
// in a global setup file: it costs ~2.5s per test file and only two files need it.
import '@testing-library/jest-dom/vitest';

import { TooltipProvider } from '@filigran/design-system';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import { IntlProvider } from 'react-intl';
import { MemoryRouter } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { expectNoMuiControls } from '../../../utils/designSystemAssertions';

// The bell's data path is not what this file is about: it covers the unread
// marker being a library Badge, and that marker actually being announced.
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

const bell = () => screen.getByRole('link', { name: 'notifications' });

describe('TopBarNotifications unread marker', () => {
  afterEach(() => {
    cleanup();
    unread = 0;
  });

  it('marks unread notifications with the library Badge, not a MUI one', async () => {
    // Compensation #22 is retired: the library shipped a Badge (#114).
    unread = 3;
    const { container } = renderBell();
    await waitFor(() => expect(bell()).toHaveAccessibleDescription('3'));
    expectNoMuiControls(container, 'the notifications bell');
  });

  it('announces the count to assistive technology, not merely into the DOM', async () => {
    // Review #7305: the previous version of this test asserted
    // `document.body.textContent`, which passed while the badge sat inside the
    // icon slot's `aria-hidden` subtree — announced to nobody. Only the COMPUTED
    // description can tell those two apart, so that is what is asserted here.
    unread = 7;
    renderBell();
    await waitFor(() => expect(bell()).toHaveAccessibleDescription('7'));
    // And the description must not have leaked into the control's NAME.
    expect(bell()).toHaveAccessibleName('notifications');
  });

  it('says nothing when everything is read', async () => {
    unread = 0;
    const { container } = renderBell();
    await waitFor(() => expect(bell()).toHaveAccessibleName('notifications'));
    expect(bell()).toHaveAccessibleDescription('');
    expectNoMuiControls(container, 'the notifications bell with nothing unread');
  });
});

import { faker } from '@faker-js/faker';
import { TooltipProvider } from '@filigran/design-system';
import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import Communication from '../../../../../../admin/components/simulations/simulation/mails/Communication';

vi.mock('../../../../../../components/i18n', () => ({
  useFormatter: () => ({
    t: (s: string) => s,
    nsdt: (d: string) => d,
  }),
}));

const TENANT_ID = faker.string.uuid();

const setPathname = (pathname: string) => {
  Object.defineProperty(window, 'location', {
    writable: true,
    value: {
      ...window.location,
      pathname,
    },
  });
};

const renderCommunication = (attachments: string[]) => render(
  <ThemeProvider theme={createTheme()}>
    <TooltipProvider>
      <Communication
        communication={{
          communication_id: faker.string.uuid(),
          communication_from: 'player@example.com',
          communication_subject: 'Subject',
          communication_content: 'A plain text mail body',
          communication_sent_at: '2026-10-29T08:00:00Z',
          communication_animation: false,
          communication_attachments: attachments,
        }}
        communicationUsers={[]}
        isTopic
      />
    </TooltipProvider>
  </ThemeProvider>,
);

describe('Communication', () => {
  beforeEach(() => {
    setPathname(`/${TENANT_ID}/admin/simulations/${faker.string.uuid()}/animation/mails`);
  });

  afterEach(() => {
    cleanup();
  });

  describe('attachment links', () => {
    // Mail attachments are not stored under their simulation's tenant yet (#8796), so their download
    // stays on the regular path, the one call of the web interface outside the tenant route.
    it('given a page under a tenant should link the attachment download to the regular path', () => {
      // Arrange
      const attachment = `/${faker.string.uuid()}/communications/${faker.string.uuid()}/report.pdf`;

      // Act
      renderCommunication([attachment]);

      // Assert
      const link = screen.getByRole('link', { name: /report\.pdf/ });
      expect(link.getAttribute('href')).toBe(`/api/communications/attachment?file=${attachment}`);
    });
  });
});

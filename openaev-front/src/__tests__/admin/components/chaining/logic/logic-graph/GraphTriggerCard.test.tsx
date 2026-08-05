import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, render, screen } from '@testing-library/react';
import { type ReactNode } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import GraphTriggerCard from '../../../../../../admin/components/chaining/logic/logic-graph/GraphTriggerCard';

// Identity translator so assertions read the English source strings.
vi.mock('../../../../../../components/i18n', () => ({
  useFormatter: () => ({
    t: (s: string) => s,
    tPick: (s: string) => s,
  }),
}));

// The card is the EVENT node; these tests pin that its title reads as an event, never as the
// trigger's technical condition-field keys (the reported "event looks like a trigger" bug).
const wrapper = ({ children }: { children: ReactNode }) => (
  <ThemeProvider theme={createTheme()}>{children}</ThemeProvider>
);

describe('GraphTriggerCard title', () => {
  afterEach(cleanup);

  it('shows the event name when it has one', () => {
    render(
      <GraphTriggerCard id="e1" name="Credentials harvested" conditionFields={['credentials']} />,
      { wrapper },
    );
    expect(screen.getByText('Credentials harvested')).toBeTruthy();
  });

  it('falls back to "Untitled event" — not the trigger condition fields — when unnamed', () => {
    render(
      <GraphTriggerCard id="e2" name="" conditionFields={['credentials', 'hostname']} />,
      { wrapper },
    );
    // The event title must not read as the listened-on trigger fields.
    expect(screen.getByText('Untitled event')).toBeTruthy();
    expect(screen.queryByText('Credentials, Hostname')).toBeNull();
  });
});

import { TooltipProvider } from '@filigran/design-system';
import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, render, screen } from '@testing-library/react';
import { type ReactElement } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import ExposureConsole from '../../../../../../../../admin/components/workspaces/custom_dashboards/widgets/viz/command_center/ExposureConsole';

vi.mock('../../../../../../../../components/i18n', () => ({ useFormatter: () => ({ t: (s: string) => s }) }));

const renderConsole = (element: ReactElement) => render(
  <ThemeProvider theme={createTheme()}><TooltipProvider>{element}</TooltipProvider></ThemeProvider>,
);

// Every band label, so the one whose colour is a design-system token is rendered too. MUI's
// `alpha()` parses its argument in JavaScript and throws on `var(--…)`, which took the whole
// widget down behind an error boundary; the tints go through `color-mix` instead.
describe('ExposureConsole', () => {
  afterEach(cleanup);

  it.each([
    [10, 'Low exposure'],
    [38, 'Moderate exposure'],
    [60, 'High exposure'],
    [90, 'Critical exposure'],
  ])('renders the band of score %i without throwing on its colour', (score, label) => {
    renderConsole(<ExposureConsole score={score} gaps={12} validations={100} platforms={[]} />);
    expect(screen.getByText(label)).toBeTruthy();
  });
});

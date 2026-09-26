import { TooltipProvider } from '@filigran/design-system';
import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, render, screen } from '@testing-library/react';
import { type ReactElement } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import PostureScore from '../../../../admin/components/assets/PostureScore';

vi.mock('../../../../components/i18n', () => ({ useFormatter: () => ({ t: (s: string) => s }) }));

const renderScore = (element: ReactElement) => render(
  <ThemeProvider theme={createTheme()}><TooltipProvider>{element}</TooltipProvider></ThemeProvider>,
);

// Same guard as the exposure console: the "Weak posture" band reads a design-system token, and
// a token cannot go through MUI's `alpha()` (it parses in JavaScript and throws).
describe('PostureScore', () => {
  afterEach(cleanup);

  it.each([
    [9, 1, '90'],
    [6, 4, '60'],
    [3, 7, '30'],
    [1, 9, '10'],
  ])('renders the band of %i met / %i missed without throwing on its colour', (success, failed, score) => {
    renderScore(<PostureScore success={success} failed={failed} breakdown={[]} />);
    expect(screen.getByText(score)).toBeTruthy();
  });
});

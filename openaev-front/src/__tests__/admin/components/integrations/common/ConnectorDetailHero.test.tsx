import { TooltipProvider } from '@filigran/design-system';
import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, render, screen } from '@testing-library/react';
import { type ReactNode } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';

vi.mock('../../../../../components/i18n', () => ({ useFormatter: () => ({ t: (key: string) => key }) }));
vi.mock('../../../../../components/common/detail/EntityDetailCommon', () => ({
  DetailHero: ({ title, chips }: {
    title: string;
    chips: ReactNode;
  }) => <div><h1>{title}</h1>{chips}</div>,
}));

import ConnectorDetailHero from '../../../../../admin/components/integrations/common/ConnectorDetailHero';

describe('ConnectorDetailHero', () => {
  afterEach(cleanup);

  it('given_aType_should_showItsLabel', () => {
    // Act
    render(
      <ThemeProvider theme={createTheme()}>
        <TooltipProvider>
          <ConnectorDetailHero title="Nmap" type="INJECTOR" external={false} />
        </TooltipProvider>
      </ThemeProvider>,
    );

    // Assert
    expect(screen.getByRole('heading', { name: 'Nmap' })).toBeTruthy();
    expect(screen.getByText('Injector')).toBeTruthy();
    expect(screen.getByText('Built-in')).toBeTruthy();
  });
});

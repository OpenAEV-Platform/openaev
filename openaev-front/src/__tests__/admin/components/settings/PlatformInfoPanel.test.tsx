import { TooltipProvider } from '@filigran/design-system';
import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { type ReactNode } from 'react';
import { IntlProvider } from 'react-intl';
import { afterEach, describe, expect, it, vi } from 'vitest';

import PlatformInfoPanel from '../../../../admin/components/settings/PlatformInfoPanel';
import ThemeDark from '../../../../components/ThemeDark';
import { type PlatformSettings } from '../../../../utils/api-types';
import type * as UtilsModule from '../../../../utils/utils';

const { mockCopyToClipboard } = vi.hoisted(() => ({ mockCopyToClipboard: vi.fn() }));

vi.mock('../../../../utils/hooks/useAI', () => ({
  default: () => ({
    enabled: false,
    xtmOneConfigured: true,
  }),
}));

vi.mock('../../../../utils/utils', async (importOriginal) => {
  const original = await importOriginal<typeof UtilsModule>();
  return {
    ...original,
    copyToClipboard: mockCopyToClipboard,
  };
});

const COMMIT = 'a59197d8cc0123456789abcdef0123456789abcd';

const theme = createTheme(ThemeDark());

const renderPanel = (settings: Partial<PlatformSettings>) => {
  const wrapper = ({ children }: { children: ReactNode }) => (
    <ThemeProvider theme={theme}>
      <IntlProvider locale="en" defaultLocale="en" onError={() => {}}>
        <TooltipProvider>
          {children}
        </TooltipProvider>
      </IntlProvider>
    </ThemeProvider>
  );
  return render(
    <PlatformInfoPanel settings={{
      platform_version: '2.4.0',
      ...settings,
    } as PlatformSettings}
    />, { wrapper });
};

describe('PlatformInfoPanel', () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('copies the version with the full commit when the version is clicked', () => {
    renderPanel({ platform_commit: COMMIT });

    const version = screen.getByRole('button', { name: '2.4.0' });

    fireEvent.click(version);
    expect(mockCopyToClipboard).toHaveBeenCalledWith(expect.any(Function), `2.4.0#${COMMIT}`);
  });

  it('details the version and the full commit in the tooltip', async () => {
    renderPanel({ platform_commit: COMMIT });

    fireEvent.pointerMove(screen.getByRole('button', { name: '2.4.0' }));

    expect(await screen.findByText('Version: 2.4.0')).toBeDefined();
    expect(screen.getByText(`Commit hash: ${COMMIT}`)).toBeDefined();
  });

  it('shows no commit when the build has none', () => {
    renderPanel({ platform_commit: '' });

    expect(screen.getByText('2.4.0')).toBeDefined();
    expect(screen.queryByRole('button')).toBeNull();
  });
});

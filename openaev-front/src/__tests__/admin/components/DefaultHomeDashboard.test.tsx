import { createTheme, ThemeProvider } from '@mui/material/styles';
import { act, cleanup, render, screen } from '@testing-library/react';
import { useEffect, useState } from 'react';
import { MemoryRouter } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { type EsSeries } from '../../../utils/api-types';

const { mockDispatch, adHocSeriesMock, gridMountSpy } = vi.hoisted(() => ({
  mockDispatch: vi.fn(),
  adHocSeriesMock: vi.fn(),
  gridMountSpy: vi.fn(),
}));

vi.mock('../../../components/i18n', () => ({
  useFormatter: () => ({
    t: (value: string) => value,
    locale: 'en',
  }),
}));

// Plain state instead of persistence: Node's non-functional localStorage
// global shadows the happy-dom one, breaking the real hook in tests.
vi.mock('usehooks-ts', () => ({ useLocalStorage: (_key: string, initialValue: unknown) => useState(initialValue) }));

vi.mock('../../../utils/hooks', () => ({ useAppDispatch: () => mockDispatch }));

vi.mock('../../../utils/hooks/useDataLoader', () => ({
  default: (loader: () => void) => {
    loader();
  },
}));

vi.mock('../../../actions/assets/securityPlatform-actions', () => ({ fetchSecurityPlatforms: () => ({ type: 'fetchSecurityPlatforms' }) }));

vi.mock(import('../../../actions/dashboards/dashboard-action'), async (importOriginal) => {
  const actual = await importOriginal();
  return {
    ...actual,
    adHocSeries: adHocSeriesMock,
  };
});

// Counts MOUNTS (not renders): the #7599 regression was the grid mounting
// eagerly with 3 gauges and being key-remounted (second mount, full refetch)
// once the Human Response probe resolved.
const GridMountProbe = () => {
  useEffect(() => {
    gridMountSpy();
  }, []);
  return <div data-testid="dashboard-grid" />;
};

vi.mock('../../../admin/components/workspaces/custom_dashboards/CustomDashboardReactLayout', () => ({ default: () => <GridMountProbe /> }));

import DefaultHomeDashboard from '../../../admin/components/default_dashboard/DefaultHomeDashboard';

const theme = createTheme();

const renderDashboard = () => render(
  <ThemeProvider theme={theme}>
    <MemoryRouter>
      <DefaultHomeDashboard />
    </MemoryRouter>
  </ThemeProvider>,
);

const probeResponse = (value: number): { data: EsSeries[] } => ({
  data: [{
    label: '',
    data: [{
      key: 'PENDING',
      value,
    }],
  }],
});

describe('DefaultHomeDashboard first load (#7599)', () => {
  beforeEach(() => {
    mockDispatch.mockClear();
    adHocSeriesMock.mockReset();
    gridMountSpy.mockClear();
  });

  afterEach(() => {
    cleanup();
  });

  it('defers the grid until the probe resolves, then mounts it exactly once with human response data', async () => {
    // Arrange
    let resolveProbe: (response: { data: EsSeries[] }) => void = () => {};
    adHocSeriesMock.mockImplementation(() => new Promise((resolve) => {
      resolveProbe = resolve;
    }));

    // Act
    renderDashboard();
    // Let the concurrency limiter actually start the probe (one microtask hop)
    await act(async () => {});

    // Assert: probe in flight, the grid must not be mounted yet
    expect(screen.queryByTestId('dashboard-grid')).toBeNull();

    // Act: the probe finds human-driven expectations in range
    await act(async () => {
      resolveProbe(probeResponse(3));
    });

    // Assert: a single mount, directly with the final gauge geometry
    expect(await screen.findByTestId('dashboard-grid')).toBeDefined();
    expect(gridMountSpy).toHaveBeenCalledTimes(1);
  });

  it('mounts the grid exactly once when the probe finds no human response data', async () => {
    // Arrange
    let resolveProbe: (response: { data: EsSeries[] }) => void = () => {};
    adHocSeriesMock.mockImplementation(() => new Promise((resolve) => {
      resolveProbe = resolve;
    }));

    // Act
    renderDashboard();
    await act(async () => {});
    expect(screen.queryByTestId('dashboard-grid')).toBeNull();
    await act(async () => {
      resolveProbe(probeResponse(0));
    });

    // Assert
    expect(await screen.findByTestId('dashboard-grid')).toBeDefined();
    expect(gridMountSpy).toHaveBeenCalledTimes(1);
  });

  it('still mounts the grid exactly once when the probe fails', async () => {
    // Arrange
    let rejectProbe: (error: Error) => void = () => {};
    adHocSeriesMock.mockImplementation(() => new Promise((_resolve, reject) => {
      rejectProbe = reject;
    }));

    // Act
    renderDashboard();
    await act(async () => {});
    expect(screen.queryByTestId('dashboard-grid')).toBeNull();
    await act(async () => {
      rejectProbe(new Error('engine warming up'));
    });

    // Assert: the gauge defaults to hidden, the dashboard still renders
    expect(await screen.findByTestId('dashboard-grid')).toBeDefined();
    expect(gridMountSpy).toHaveBeenCalledTimes(1);
  });
});

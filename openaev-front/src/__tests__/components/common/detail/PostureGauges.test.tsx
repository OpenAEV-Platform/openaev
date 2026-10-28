import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import PostureGauges from '../../../../components/common/detail/PostureGauges';
import { type ExpectationResultsByType } from '../../../../utils/api-types';

vi.mock('../../../../components/i18n', () => ({ useFormatter: () => ({ t: (value: string) => value }) }));
vi.mock('../../../../utils/hooks/useCountUp', () => ({ default: (value: number) => value }));

const renderGauges = (expectationResultsByTypes: ExpectationResultsByType[]) => render(
  <ThemeProvider theme={createTheme()}>
    <PostureGauges expectationResultsByTypes={expectationResultsByTypes} />
  </ThemeProvider>,
);

const prevention = (distribution: ExpectationResultsByType['distribution']): ExpectationResultsByType => ({
  type: 'PREVENTION',
  avgResult: 'SUCCESS',
  distribution,
});

describe('PostureGauges', () => {
  afterEach(cleanup);

  it('includes pending expectations in the displayed percentage and ring segments', () => {
    const distribution = [
      { id: 'PREVENTED', label: 'Prevented', value: 1 },
      { id: 'PENDING', label: 'Pending', value: 1 },
    ];

    const { container } = renderGauges([prevention(distribution)]);

    expect(screen.getByText('50%')).toBeTruthy();
    expect(container.querySelectorAll('svg > circle')).toHaveLength(3);
  });

  it.each([
    [[
      { id: 'PREVENTED', label: 'Prevented', value: 1 },
      { id: 'FAILED', label: 'Failed', value: 1 },
    ], '50%'],
    [[{ id: 'PENDING', label: 'Pending', value: 2 }], '0%'],
  ])('calculates resilience from the complete distribution: %s', (distribution, percentage) => {
    renderGauges([prevention(distribution)]);

    expect(screen.getByText(percentage)).toBeTruthy();
  });

  it('keeps the no-data placeholder for an empty distribution', () => {
    renderGauges([prevention([])]);

    expect(screen.getByText('-')).toBeTruthy();
  });
});

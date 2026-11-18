import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import ResilienceGaugeWidget from '../../../../../../../admin/components/workspaces/custom_dashboards/widgets/viz/ResilienceGaugeWidget';
import { type SerieData } from '../../../../../../../admin/components/workspaces/custom_dashboards/widgets/WidgetViz';
import { type StructuralHistogramWidget } from '../../../../../../../utils/api-types';

vi.mock('../../../../../../../components/i18n', () => ({ useFormatter: () => ({ t: (value: string) => value }) }));
vi.mock('../../../../../../../utils/hooks/useCountUp', () => ({ default: (value: number) => value }));

const widgetConfig = { series: [] } as unknown as StructuralHistogramWidget;

const renderWidget = (datas: SerieData[]) => render(
  <ThemeProvider theme={createTheme()}>
    <ResilienceGaugeWidget widgetId="widget" widgetConfig={widgetConfig} datas={datas} />
  </ThemeProvider>,
);

const serie = (x: string, y: number) => ({
  x,
  y,
  meta: x,
}) as unknown as SerieData;

describe('ResilienceGaugeWidget', () => {
  afterEach(cleanup);

  it('counts pending expectations in the percentage, like the simulation Results gauges', () => {
    renderWidget([serie('SUCCESS', 14), serie('FAILED', 20), serie('PENDING', 28)]);

    // 14 / 62, not 14 / 34 (41%).
    expect(screen.getByText('23%')).toBeTruthy();
  });

  it('charts partial results as their own arc and legend entry', () => {
    const { container } = renderWidget([serie('SUCCESS', 1), serie('PARTIAL', 1), serie('FAILED', 3)]);

    expect(screen.getByText('PARTIAL')).toBeTruthy();
    // Track plus one arc per non-empty bucket: success, partial, failed.
    expect(container.querySelectorAll('svg circle')).toHaveLength(4);
  });

  it('shows 0% in grey when every expectation is pending', () => {
    const theme = createTheme();
    renderWidget([serie('PENDING', 2)]);

    expect(screen.getByText('0%').getAttribute('fill')).toBe(theme.palette.text.disabled);
  });

  it('keeps the red scale once one expectation is resolved', () => {
    const theme = createTheme();
    renderWidget([serie('FAILED', 1), serie('PENDING', 1)]);

    expect(screen.getByText('0%').getAttribute('fill')).toBe(theme.palette.error.main);
  });
});

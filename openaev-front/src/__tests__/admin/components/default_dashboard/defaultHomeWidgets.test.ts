import { describe, expect, it } from 'vitest';

import { buildDefaultHomeWidgets, gaugeRowWidths } from '../../../../admin/components/default_dashboard/defaultHomeWidgets';

describe('defaultHomeWidgets', () => {
  describe('Function: gaugeRowWidths()', () => {
    it.each([
      [3, [4, 4, 4]],
      [4, [3, 3, 3, 3]],
      [5, [3, 3, 2, 2, 2]],
    ])('given %i gauges, should fill the row with %j', (count, expected) => {
      // -- ACT & ASSERT --
      expect(gaugeRowWidths(count)).toEqual(expected);
    });
  });

  describe('Function: buildDefaultHomeWidgets()', () => {
    it.each([
      [false, ['default-prevention', 'default-detection', 'default-vulnerability', 'default-human-response']],
      [true, ['default-prevention', 'default-detection', 'default-logging', 'default-vulnerability', 'default-human-response']],
    ])('given Logging enabled: %s, should lay out the gauges %j side by side on a full row', (includeLogging, expectedIds) => {
      // -- ACT --
      const gauges = buildDefaultHomeWidgets('LAST_DAY', undefined, true, includeLogging)
        .filter(widget => widget.widget_type === 'resilience-gauge');

      // -- ASSERT --
      expect(gauges.map(gauge => gauge.widget_id)).toEqual(expectedIds);
      const lastGauge = gauges[gauges.length - 1].widget_layout;
      expect(lastGauge.widget_layout_x + lastGauge.widget_layout_w).toBe(12);
      gauges.slice(1).forEach((gauge, index) => {
        const previous = gauges[index].widget_layout;
        expect(gauge.widget_layout.widget_layout_x).toBe(previous.widget_layout_x + previous.widget_layout_w);
      });
    });
  });
});

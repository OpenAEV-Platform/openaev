import { type Theme } from '@mui/material';
import { describe, expect, it } from 'vitest';

import { donutChartOptions, polarAreaChartOptions, simpleLabelTooltip } from '../../utils/Charts';

interface ThemeOverrides {
  background?: { nav?: unknown };
  text?: { primary?: unknown };
}

const buildTheme = (overrides: ThemeOverrides = {}) => ({
  palette: {
    background: {
      nav: '#0a0a0a',
      ...overrides.background,
    },
    text: {
      primary: '#ffffff',
      ...overrides.text,
    },
  },
}) as Theme;

type TooltipCallback = (opts: {
  seriesIndex: number;
  w: { config: { labels: unknown[] } };
}) => string;

const renderTooltip = (theme: Theme, label: unknown) => {
  const callback = simpleLabelTooltip(theme) as unknown as TooltipCallback;
  return callback({
    seriesIndex: 0,
    w: { config: { labels: [label] } },
  });
};

describe('Charts utils', () => {
  describe('Function: simpleLabelTooltip()', () => {
    it('given a safe label and theme colors, should render them unchanged', () => {
      const theme = buildTheme();
      const html = renderTooltip(theme, 'Organization ABC');
      expect(html).toContain('Organization ABC');
      expect(html).toContain('#0a0a0a');
      expect(html).toContain('#ffffff');
    });

    it('given a malicious entity label (stored XSS payload), should escape it', () => {
      const theme = buildTheme();
      const maliciousLabel = '<img src=x onerror=alert(1)>';
      const html = renderTooltip(theme, maliciousLabel);
      expect(html).not.toContain(maliciousLabel);
      expect(html).not.toContain('<img');
      expect(html).toContain('&lt;img');
    });

    it('given a malicious theme background color (theme injection), should fall back to a safe value', () => {
      const theme = buildTheme({ background: { nav: '"><script>alert(1)</script>' } });
      const html = renderTooltip(theme, 'label');
      expect(html).not.toContain('<script>');
      expect(html).not.toContain('"><');
      expect(html).toContain('background: inherit');
    });

    it('given a malicious theme text color, should fall back to a safe value', () => {
      const theme = buildTheme({ text: { primary: '"><svg onload=alert(1)>' } });
      const html = renderTooltip(theme, 'label');
      expect(html).not.toContain('<svg');
      expect(html).not.toContain('"><');
      expect(html).toContain('color: inherit');
    });

    it('given a color value breaking out of the style attribute via a quote, should fall back to a safe value', () => {
      const theme = buildTheme({ text: { primary: '" onmouseover="alert(1)' } });
      const html = renderTooltip(theme, 'label');
      expect(html).not.toContain('" onmouseover="');
      expect(html).toContain('color: inherit');
    });

    it('given a color value trying to inject extra CSS declarations via a semicolon, should fall back to a safe value', () => {
      const theme = buildTheme({ background: { nav: 'red; background-image: url(https://evil.example/leak)' } });
      const html = renderTooltip(theme, 'label');
      expect(html).not.toContain('background-image');
      expect(html).toContain('background: inherit');
    });

    it('given a well-formed 6-digit hex color, should accept it', () => {
      const theme = buildTheme({ background: { nav: '#123abc' } });
      const html = renderTooltip(theme, 'label');
      expect(html).toContain('background: #123abc');
    });

    it('given an undefined label, should not throw and render an empty string', () => {
      const theme = buildTheme();
      expect(() => renderTooltip(theme, undefined)).not.toThrow();
    });

    it('given a non-string label (number), should coerce it to a string', () => {
      const theme = buildTheme();
      const html = renderTooltip(theme, 42);
      expect(html).toContain('42');
    });

    it('given undefined theme color values, should not throw', () => {
      const theme = buildTheme({
        background: { nav: undefined },
        text: { primary: undefined },
      });
      expect(() => renderTooltip(theme, 'label')).not.toThrow();
    });
  });

  describe('Function: donutChartOptions() legend.formatter', () => {
    it('given a malicious label, should escape it before ApexCharts renders it as legend innerHTML', () => {
      const theme = buildTheme();
      const maliciousLabel = '<img src=x onerror=alert(1)>';
      const options = donutChartOptions({
        theme,
        labels: [maliciousLabel],
      });
      const legendFormatter = options.legend?.formatter as (name: string) => string;
      const output = legendFormatter(maliciousLabel);
      expect(output).not.toContain('<img');
      expect(output).toContain('&lt;img');
    });
  });

  describe('Function: donutChartOptions() tooltip.y.title.formatter', () => {
    it('given a malicious label, should escape the series name before ApexCharts renders it as the default tooltip y-label innerHTML', () => {
      // Arrange
      const theme = buildTheme();
      const maliciousLabel = '<img src=x onerror=alert(1)>';
      const options = donutChartOptions({
        theme,
        labels: [maliciousLabel],
      });

      // Act: ApexCharts calls tooltip.y.title.formatter with the raw series name
      // (a copy of config.labels) then assigns the result to `ttYLabel.innerHTML`.
      const yTitleFormatter = (options.tooltip?.y as { title?: { formatter?: (name: string) => string } })?.title?.formatter as (name: string) => string;
      const output = yTitleFormatter(maliciousLabel);

      // Assert
      expect(output).not.toContain('<img');
      expect(output).toContain('&lt;img');
    });

    it('given a legitimate label containing an ampersand, should escape it once (no double-escaping across disjoint paths)', () => {
      // Arrange
      const theme = buildTheme();
      const options = donutChartOptions({
        theme,
        labels: ['R&D'],
      });

      // Act
      const yTitleFormatter = (options.tooltip?.y as { title?: { formatter?: (name: string) => string } })?.title?.formatter as (name: string) => string;
      const output = yTitleFormatter('R&D');

      // Assert: escaped exactly once so innerHTML renders "R&D", not "R&amp;D"
      expect(output).toBe('R&amp;D');
    });
  });

  describe('Function: polarAreaChartOptions() tooltip.y.title.formatter', () => {
    it('given a malicious label, should escape the series name before ApexCharts renders it as the default tooltip y-label innerHTML', () => {
      // Arrange
      const theme = buildTheme();
      const maliciousLabel = '<img src=x onerror=alert(1)>';
      const options = polarAreaChartOptions(theme, [maliciousLabel]);

      // Act
      const yTitleFormatter = (options.tooltip?.y as { title?: { formatter?: (name: string) => string } })?.title?.formatter as (name: string) => string;
      const output = yTitleFormatter(maliciousLabel);

      // Assert
      expect(output).not.toContain('<img');
      expect(output).toContain('&lt;img');
    });
  });
});

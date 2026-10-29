import '@testing-library/jest-dom/vitest';

import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import { IntlProvider } from 'react-intl';
import { afterAll, afterEach, beforeAll, describe, expect, it, vi } from 'vitest';

import TimeRangeFilters from '../../../../../admin/components/workspaces/custom_dashboards/TimeRangeFilters';
import { CUSTOM_TIME_RANGE } from '../../../../../admin/components/workspaces/custom_dashboards/widgets/configuration/common/TimeRangeUtils';

const ORIGINAL_TZ = process.env.TZ;
const NOW = new Date('2026-10-02T10:00:00.000Z');

// The library panel is a Radix popover; its primitives measure their anchor.
beforeAll(() => {
  if (!('ResizeObserver' in globalThis)) {
    (globalThis as unknown as { ResizeObserver: unknown }).ResizeObserver = class {
      observe() {}
      unobserve() {}
      disconnect() {}
    };
  }
});
afterEach(() => {
  cleanup();
  vi.useRealTimers();
});
afterAll(() => {
  process.env.TZ = ORIGINAL_TZ;
});

const dayName = (year: number, month: number, date: number) => new Intl.DateTimeFormat('en', {
  weekday: 'long',
  year: 'numeric',
  month: 'long',
  day: 'numeric',
}).format(new Date(year, month, date));

const renderFilters = (values: {
  start?: string;
  end?: string;
}) => {
  const handleStartDate = vi.fn();
  const handleEndDate = vi.fn();
  render(
    <IntlProvider locale="en" defaultLocale="en" onError={() => {}}>
      <TimeRangeFilters
        handleTimeRange={vi.fn()}
        handleStartDate={handleStartDate}
        handleEndDate={handleEndDate}
        timeRangeValue={CUSTOM_TIME_RANGE}
        startDateValue={values.start}
        endDateValue={values.end}
      />
    </IntlProvider>,
  );
  return {
    handleStartDate,
    handleEndDate,
  };
};

const openCalendar = async (field: 'start' | 'end') => {
  fireEvent.click(screen.getAllByRole('button', { name: 'Open calendar' })[field === 'start' ? 0 : 1]);
  await act(async () => {});
};

describe('TimeRangeFilters', () => {
  describe.each(['Europe/Paris', 'America/New_York', 'UTC'])('in %s', (timeZone) => {
    it('stores UTC midnight of the picked day when the start already holds a UTC midnight', async () => {
      // Arrange
      process.env.TZ = timeZone;
      vi.useFakeTimers({
        toFake: ['Date'],
        now: NOW,
      });
      const { handleStartDate } = renderFilters({
        start: '2026-10-02T00:00:00.000Z',
        end: '2026-10-10T00:00:00.000Z',
      });

      // Act
      await openCalendar('start');
      fireEvent.click(screen.getByRole('gridcell', { name: dayName(2026, 9, 3) }));

      // Assert
      expect(handleStartDate).toHaveBeenCalledWith('2026-10-03T00:00:00.000Z');
    });

    it('stores UTC midnight of the picked day when the start is empty', async () => {
      // Arrange
      process.env.TZ = timeZone;
      vi.useFakeTimers({
        toFake: ['Date'],
        now: NOW,
      });
      const { handleStartDate } = renderFilters({});

      // Act
      await openCalendar('start');
      fireEvent.click(screen.getByRole('gridcell', { name: dayName(2026, 9, 3) }));

      // Assert
      expect(handleStartDate).toHaveBeenCalledWith('2026-10-03T00:00:00.000Z');
    });

    it('stores UTC midnight of the picked end day', async () => {
      // Arrange
      process.env.TZ = timeZone;
      vi.useFakeTimers({
        toFake: ['Date'],
        now: NOW,
      });
      const { handleEndDate } = renderFilters({
        start: '2026-10-02T00:00:00.000Z',
        end: '2026-10-10T00:00:00.000Z',
      });

      // Act
      await openCalendar('end');
      fireEvent.click(screen.getByRole('gridcell', { name: dayName(2026, 9, 12) }));

      // Assert
      expect(handleEndDate).toHaveBeenCalledWith('2026-10-12T00:00:00.000Z');
    });

    it('shows a stored UTC midnight as that same day', async () => {
      // Arrange
      process.env.TZ = timeZone;
      vi.useFakeTimers({
        toFake: ['Date'],
        now: NOW,
      });
      renderFilters({
        start: '2026-10-02T00:00:00.000Z',
        end: '2026-10-10T00:00:00.000Z',
      });

      // Act
      await openCalendar('start');

      // Assert
      expect(screen.getByRole('gridcell', { name: dayName(2026, 9, 2) })).toHaveAttribute('aria-selected', 'true');
    });
  });
});

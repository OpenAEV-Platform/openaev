import '@testing-library/jest-dom/vitest';

import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterAll, afterEach, beforeAll, describe, expect, it, vi } from 'vitest';

import SchedulingDialog from '../../../../../admin/components/common/scheduling/SchedulingDialog';

// Shows each date as the local day it falls on, so the summary tells which day the user sees.
const localDay = (date: Date | string) => {
  const d = new Date(date);
  return `day:${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
};

vi.mock('../../../../../components/i18n', () => ({
  useFormatter: () => ({
    t: (key: string, values?: Record<string, string>) => (values
      ? key.replace(/\{(\w+)\}/g, (_, name: string) => values[name])
      : key),
    locale: 'en',
    fld: localDay,
  }),
}));

const ORIGINAL_TZ = process.env.TZ;

// The library date picker measures its anchor.
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

// 21 November 2026 at 01:00 UTC: still 20 November in Los Angeles, already 21 November in Paris.
const NOW = new Date('2026-11-21T01:00:00.000Z');

describe('SchedulingDialog', () => {
  describe.each([
    {
      timeZone: 'America/Los_Angeles',
      today: [2026, 10, 20],
    },
    {
      timeZone: 'Europe/Paris',
      today: [2026, 10, 21],
    },
  ])('in $timeZone', ({ timeZone, today }) => {
    const renderDialog = (initialValues = {}) => {
      process.env.TZ = timeZone;
      vi.useFakeTimers({
        toFake: ['Date'],
        now: NOW,
      });
      const onSubmit = vi.fn();
      render(<SchedulingDialog open onClose={vi.fn()} initialValues={initialValues} onSubmit={onSubmit} />);
      return onSubmit;
    };

    it('given_aNewSchedule_should_startAtTheBeginningOfTodayInTheUserTimeZone', async () => {
      // Arrange
      const onSubmit = renderDialog();
      const [year, month, date] = today;
      const startOfToday = new Date(year, month, date);

      // Act
      fireEvent.click(screen.getByRole('button', { name: 'Save' }));

      // Assert
      await waitFor(() => expect(onSubmit).toHaveBeenCalled());
      expect(onSubmit.mock.calls[0][1]).toBe(startOfToday.toISOString());
      expect(screen.getByText(new RegExp(localDay(startOfToday)))).toBeInTheDocument();
    });

    it('given_aSavedOneOffSchedule_should_showItsDayAndSendItBackUnchanged', async () => {
      // Arrange: what the dialog saves for "once on 25 November": that day, from midnight to midnight
      process.env.TZ = timeZone;
      const start = new Date(2026, 10, 25).toISOString();
      const end = new Date(2026, 10, 26).toISOString();
      const onSubmit = renderDialog({
        recurrence: '0 0 8 * * *',
        recurrenceStart: start,
        recurrenceEnd: end,
      });

      // Act
      fireEvent.click(screen.getByRole('button', { name: 'Save' }));

      // Assert
      expect(screen.getByText(/Runs once on day:2026-11-25/)).toBeInTheDocument();
      await waitFor(() => expect(onSubmit).toHaveBeenCalled());
      expect(onSubmit.mock.calls[0][1]).toBe(start);
      expect(onSubmit.mock.calls[0][2]).toBe(end);
    });

    it('given_aDayPickedInTheCalendar_should_showAndSendThatDay', async () => {
      // Arrange
      const onSubmit = renderDialog();

      // Act: pick 25 November in the start date calendar
      fireEvent.click(screen.getAllByRole('button', { name: 'Open calendar' })[0]);
      fireEvent.click(await screen.findByRole('gridcell', { name: /November 25, 2026/ }));

      // Assert: the field, the summary and the saved start all say 25 November
      await waitFor(() => expect(screen.getByLabelText('Start date')).toHaveValue('11/25/2026'));
      expect(screen.getByText(/day:2026-11-25/)).toBeInTheDocument();
      fireEvent.click(screen.getByRole('button', { name: 'Save' }));
      await waitFor(() => expect(onSubmit).toHaveBeenCalled());
      expect(onSubmit.mock.calls[0][1]).toBe(new Date(2026, 10, 25).toISOString());
    });
  });

  it.each([
    {
      kind: 'one-off',
      endDay: 26,
    },
    {
      kind: 'daily',
      endDay: 30,
    },
  ])('given_aScheduleSavedInLosAngeles_should_keepItsDatesWhenSavedUnchangedInParis ($kind)', async ({ endDay }) => {
    // Arrange: dates saved from Los Angeles, then the dialog opened in Paris
    process.env.TZ = 'America/Los_Angeles';
    const start = new Date(2026, 10, 25).toISOString();
    const end = new Date(2026, 10, endDay).toISOString();
    process.env.TZ = 'Europe/Paris';
    vi.useFakeTimers({
      toFake: ['Date'],
      now: NOW,
    });
    const onSubmit = vi.fn();
    render(
      <SchedulingDialog
        open
        onClose={vi.fn()}
        initialValues={{
          recurrence: '0 0 7 * * *',
          recurrenceStart: start,
          recurrenceEnd: end,
        }}
        onSubmit={onSubmit}
      />,
    );

    // Act
    fireEvent.click(screen.getByRole('button', { name: 'Save' }));

    // Assert
    await waitFor(() => expect(onSubmit).toHaveBeenCalled());
    expect(onSubmit.mock.calls[0][1]).toBe(start);
    expect(onSubmit.mock.calls[0][2]).toBe(end);
  });
});

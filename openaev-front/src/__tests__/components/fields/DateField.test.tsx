import '@testing-library/jest-dom/vitest';

import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import { type ReactNode, useState } from 'react';
import { FormProvider, useForm } from 'react-hook-form';
import { IntlProvider } from 'react-intl';
import { afterAll, afterEach, beforeAll, describe, expect, it, vi } from 'vitest';

import DateField from '../../../components/fields/DateField';

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

afterEach(cleanup);

const saveLabel = 'Save';

// The field reads the product locale and the calendar's translated names.
const withIntl = (node: ReactNode) => (
  <IntlProvider locale="en" defaultLocale="en" onError={() => {}}>
    {node}
  </IntlProvider>
);

const typeDate = (label: string, text: string) => {
  const input = screen.getByLabelText(label) as HTMLInputElement;
  fireEvent.change(input, { target: { value: text } });
  fireEvent.blur(input);
  return input;
};

const FormHarness = ({ onValues, ...props }: {
  onValues: (v: Record<string, unknown>) => void;
  clearedValue?: string;
  toStorage?: (d: Date) => string;
}) => {
  const methods = useForm<{ when: string }>({ defaultValues: { when: '' } });
  return (
    <FormProvider {...methods}>
      <form onSubmit={methods.handleSubmit(onValues)}>
        <DateField name="when" label="When" format="yyyy-MM-dd" {...props} />
        <button type="submit">{saveLabel}</button>
      </form>
    </FormProvider>
  );
};

describe('DateField', () => {
  it('stores the picked date as an ISO string on the form path', async () => {
    let submitted: Record<string, unknown> | undefined;
    render(withIntl(
      <FormHarness onValues={(v) => {
        submitted = v;
      }}
      />,
    ));
    typeDate('When', '2026-12-24');
    fireEvent.click(screen.getByText(saveLabel));
    await screen.findByText(saveLabel);
    // Local midnight on the typed day, which is the day before in UTC east of it.
    expect(submitted?.when).toBe(new Date(2026, 11, 24).toISOString());
  });

  it('applies toStorage instead of the plain ISO form', async () => {
    let submitted: Record<string, unknown> | undefined;
    render(withIntl(
      <FormHarness
        onValues={(v) => {
          submitted = v;
        }}
        toStorage={d => `day:${d.getFullYear()}-${d.getMonth() + 1}-${d.getDate()}`}
      />,
    ));
    typeDate('When', '2026-12-24');
    fireEvent.click(screen.getByText(saveLabel));
    await screen.findByText(saveLabel);
    expect(submitted?.when).toBe('day:2026-12-24');
  });

  it('writes clearedValue when the field is emptied', async () => {
    let submitted: Record<string, unknown> | undefined;
    render(withIntl(
      <FormHarness
        onValues={(v) => {
          submitted = v;
        }}
        clearedValue=""
      />,
    ));
    typeDate('When', '2026-12-24');
    typeDate('When', '');
    fireEvent.click(screen.getByText(saveLabel));
    await screen.findByText(saveLabel);
    expect(submitted?.when).toBe('');
  });

  it('drives a controlled field without a form', () => {
    const Controlled = () => {
      const [value, setValue] = useState<Date | null>(null);
      return (
        <>
          <DateField label="Filter" format="yyyy-MM-dd" value={value} onChange={setValue} />
          <span data-testid="out">{value ? value.getFullYear() : 'empty'}</span>
        </>
      );
    };
    render(withIntl(<Controlled />));
    expect(screen.getByTestId('out')).toHaveTextContent('empty');
    typeDate('Filter', '2026-12-24');
    expect(screen.getByTestId('out')).toHaveTextContent('2026');
  });
});

// Expected values measured on @mui/x-date-pickers 8.29.3, the picker this field
// replaced: a date-only pick keeps the wall-clock time of the current value, or
// lands on local midnight when the field is empty.
describe('DateField day pick — parity with the MUI X picker it replaced', () => {
  const ORIGINAL_TZ = process.env.TZ;
  const NOW = new Date('2026-10-02T10:00:00.000Z');
  const UTC_MIDNIGHT_OCT_2 = '2026-10-02T00:00:00.000Z';

  afterEach(() => {
    vi.useRealTimers();
  });
  afterAll(() => {
    process.env.TZ = ORIGINAL_TZ;
  });

  const pickOnCalendar = async (day: Date) => {
    fireEvent.click(screen.getByRole('button', { name: 'Open calendar' }));
    await act(async () => {});
    const name = new Intl.DateTimeFormat('en', {
      weekday: 'long',
      year: 'numeric',
      month: 'long',
      day: 'numeric',
    }).format(day);
    // The scenarios pick at most one month after the one the calendar opens on.
    if (!screen.queryByRole('gridcell', { name })) {
      fireEvent.click(screen.getByRole('button', { name: 'Next month' }));
      await act(async () => {});
    }
    fireEvent.click(screen.getByRole('gridcell', { name }));
  };

  const Controlled = ({ initial, onPick }: {
    initial: Date | null;
    onPick: (d: Date | null) => void;
  }) => {
    const [value, setValue] = useState(initial);
    return (
      <DateField
        label="When"
        value={value}
        onChange={(d) => {
          onPick(d);
          setValue(d);
        }}
      />
    );
  };

  describe.each([
    {
      timeZone: 'Europe/Paris',
      emptyToOct3: '2026-10-02T22:00:00.000Z',
      utcMidnightToOct3: '2026-10-03T00:00:00.000Z',
      utcMidnightToNov3: '2026-11-03T01:00:00.000Z',
    },
    {
      timeZone: 'America/New_York',
      emptyToOct3: '2026-10-03T04:00:00.000Z',
      utcMidnightToOct3: '2026-10-04T00:00:00.000Z',
      utcMidnightToNov3: '2026-11-04T01:00:00.000Z',
    },
    {
      timeZone: 'UTC',
      emptyToOct3: '2026-10-03T00:00:00.000Z',
      utcMidnightToOct3: '2026-10-03T00:00:00.000Z',
      utcMidnightToNov3: '2026-11-03T00:00:00.000Z',
    },
  ])('in $timeZone', ({ timeZone, emptyToOct3, utcMidnightToOct3, utcMidnightToNov3 }) => {
    it.each([
      {
        scenario: 'empty field, pick Oct 3',
        initial: null,
        pick: [2026, 9, 3],
        expected: emptyToOct3,
      },
      {
        scenario: 'UTC midnight value, pick Oct 3',
        initial: UTC_MIDNIGHT_OCT_2,
        pick: [2026, 9, 3],
        expected: utcMidnightToOct3,
      },
      {
        scenario: 'UTC midnight value, pick Nov 3 (across DST)',
        initial: UTC_MIDNIGHT_OCT_2,
        pick: [2026, 10, 3],
        expected: utcMidnightToNov3,
      },
    ])('$scenario', async ({ initial, pick, expected }) => {
      // Arrange
      process.env.TZ = timeZone;
      vi.useFakeTimers({
        toFake: ['Date'],
        now: NOW,
      });
      const picked: (Date | null)[] = [];
      // Built once the zone is set: the local day the calendar shows.
      const [year, month, date] = pick;
      const day = new Date(year, month, date);
      render(withIntl(<Controlled initial={initial ? new Date(initial) : null} onPick={d => picked.push(d)} />));

      // Act
      await pickOnCalendar(day);

      // Assert
      expect(picked.at(-1)?.toISOString()).toBe(expected);
    });

    it('keeps the time on the form path too, typed or picked', async () => {
      // Arrange
      process.env.TZ = timeZone;
      vi.useFakeTimers({
        toFake: ['Date'],
        now: NOW,
      });
      let submitted: Record<string, unknown> | undefined;
      const Form = () => {
        const methods = useForm<{ when: string }>({ defaultValues: { when: UTC_MIDNIGHT_OCT_2 } });
        return (
          <FormProvider {...methods}>
            <form onSubmit={methods.handleSubmit((v) => {
              submitted = v;
            })}
            >
              <DateField name="when" label="When" format="yyyy-MM-dd" />
              <button type="submit">{saveLabel}</button>
            </form>
          </FormProvider>
        );
      };
      render(withIntl(<Form />));

      // Act
      typeDate('When', '2026-10-03');
      fireEvent.click(screen.getByText(saveLabel));
      await screen.findByText(saveLabel);

      // Assert
      expect(submitted?.when).toBe(utcMidnightToOct3);
    });
  });
});

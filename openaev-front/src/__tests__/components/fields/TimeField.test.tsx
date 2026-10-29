import '@testing-library/jest-dom/vitest';

import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { type ReactNode } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { IntlProvider } from 'react-intl';
import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest';

import TimeField from '../../../components/fields/TimeField';

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

const withIntl = (node: ReactNode) => (
  <IntlProvider locale="en" defaultLocale="en" onError={() => {}}>
    {node}
  </IntlProvider>
);

const label = 'Time of day';

// The reporting schedule's shape: a wall clock kept as an HH:mm string, in a
// form that only shows an error once the field has been touched.
const ClockHarness = ({ onBlurSpy }: { onBlurSpy?: () => void }) => {
  const { control } = useForm<{ time: string }>({
    mode: 'onTouched',
    defaultValues: { time: '08:00' },
  });
  return (
    <Controller
      control={control}
      name="time"
      rules={{ required: 'Should not be empty' }}
      render={({ field, fieldState: { error } }) => (
        <TimeField
          label={label}
          format="HH:mm"
          error={error?.message}
          value={field.value ? new Date(1970, 0, 1, Number(field.value.slice(0, 2)), Number(field.value.slice(3, 5))) : null}
          onChange={next => field.onChange(next ? '07:45' : '')}
          onBlur={() => {
            field.onBlur();
            onBlurSpy?.();
          }}
        />
      )}
    />
  );
};

describe('TimeField', () => {
  it('forwards the blur, so a form validating on touch can show its error', async () => {
    const spy = vi.fn();
    render(withIntl(<ClockHarness onBlurSpy={spy} />));
    const input = screen.getByLabelText(label) as HTMLInputElement;

    fireEvent.change(input, { target: { value: '' } });
    fireEvent.blur(input);

    expect(spy).toHaveBeenCalled();
    await waitFor(() => expect(screen.getByText('Should not be empty')).toBeInTheDocument());
  });
});

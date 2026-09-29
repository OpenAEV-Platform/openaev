import '@testing-library/jest-dom/vitest';

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { type ReactNode, useState } from 'react';
import { FormProvider, useForm } from 'react-hook-form';
import { IntlProvider } from 'react-intl';
import { afterEach, beforeAll, describe, expect, it } from 'vitest';

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

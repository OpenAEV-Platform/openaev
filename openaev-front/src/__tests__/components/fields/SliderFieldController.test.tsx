import '@testing-library/jest-dom/vitest';

import { zodResolver } from '@hookform/resolvers/zod';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { FormProvider, useForm } from 'react-hook-form';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { z } from 'zod';

import SliderFieldController from '../../../components/fields/SliderFieldController';

afterEach(cleanup);

const SAVE = 'Save';

interface Values { score: number }

const Harness = ({ onValues, defaultScore = 0, disabled = false }: {
  onValues: (values: Values) => void;
  defaultScore?: number;
  disabled?: boolean;
}) => {
  const methods = useForm<Values>({ defaultValues: { score: defaultScore } });
  return (
    <FormProvider {...methods}>
      <form onSubmit={methods.handleSubmit(onValues)}>
        <SliderFieldController name="score" label="Global evaluation" step={10} min={0} max={100} disabled={disabled} />
        <button type="submit">{SAVE}</button>
      </form>
    </FormProvider>
  );
};

describe('SliderFieldController', () => {
  it('shows the form value on a labelled library slider', () => {
    render(<Harness onValues={() => {}} defaultScore={40} />);
    const thumb = screen.getByRole('slider', { name: 'Global evaluation' });
    expect(thumb).toHaveAttribute('aria-valuenow', '40');
    expect(thumb).toHaveAttribute('aria-valuemin', '0');
    expect(thumb).toHaveAttribute('aria-valuemax', '100');
    expect(document.querySelector('.MuiSlider-root')).toBeNull();
  });

  it('submits a plain number, not an array, after a keyboard move', async () => {
    const onValues = vi.fn();
    render(<Harness onValues={onValues} />);
    const thumb = screen.getByRole('slider', { name: 'Global evaluation' });
    fireEvent.keyDown(thumb, { key: 'ArrowRight' });
    expect(thumb).toHaveAttribute('aria-valuenow', '10');
    fireEvent.click(screen.getByRole('button', { name: SAVE }));
    await waitFor(() => expect(onValues.mock.calls[0][0]).toEqual({ score: 10 }));
  });

  it('falls back to the minimum when the form holds no number', () => {
    const Empty = () => {
      const methods = useForm<{ score?: number }>({ defaultValues: {} });
      return (
        <FormProvider {...methods}>
          <SliderFieldController name="score" ariaLabel="Score" min={5} max={50} />
        </FormProvider>
      );
    };
    render(<Empty />);
    expect(screen.getByRole('slider', { name: 'Score' })).toHaveAttribute('aria-valuenow', '5');
  });

  it('does not move when disabled', () => {
    render(<Harness onValues={() => {}} defaultScore={20} disabled />);
    const thumb = screen.getByRole('slider', { name: 'Global evaluation' });
    fireEvent.keyDown(thumb, { key: 'ArrowRight' });
    expect(thumb).toHaveAttribute('aria-valuenow', '20');
  });
});

describe('SliderFieldController validation', () => {
  it('shows the Zod message under the label when the value is rejected', async () => {
    const Invalid = () => {
      const methods = useForm<Values>({
        defaultValues: { score: 0 },
        resolver: zodResolver(z.object({ score: z.number().min(10, { message: 'Too low' }) })),
      });
      return (
        <FormProvider {...methods}>
          <form onSubmit={methods.handleSubmit(() => {})}>
            <SliderFieldController name="score" label="Global evaluation" />
            <button type="submit">{SAVE}</button>
          </form>
        </FormProvider>
      );
    };
    render(<Invalid />);
    fireEvent.click(screen.getByRole('button', { name: SAVE }));
    expect(await screen.findByText('Too low')).toBeInTheDocument();
  });
});

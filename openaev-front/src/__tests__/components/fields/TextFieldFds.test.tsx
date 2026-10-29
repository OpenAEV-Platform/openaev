import '@testing-library/jest-dom/vitest';

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import TextFieldFds from '../../../components/fields/TextFieldFds';

afterEach(cleanup);

describe('TextFieldFds', () => {
  it('renders a labelled library input and forwards typing', () => {
    const onChange = vi.fn();
    render(<TextFieldFds label="Name" required value="" onChange={onChange} />);
    const input = screen.getByRole('textbox', { name: 'Name' });
    expect(input).toHaveAttribute('aria-required', 'true');
    expect(document.querySelector('.MuiTextField-root')).toBeNull();
    fireEvent.change(input, { target: { value: 'abc' } });
    expect(onChange).toHaveBeenCalledTimes(1);
  });

  it('shows the message as the error and marks the control invalid', () => {
    render(<TextFieldFds label="Name" error helperText="Should not be empty" defaultValue="" />);
    const input = screen.getByRole('textbox', { name: 'Name' });
    expect(input).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByText('Should not be empty')).toBeInTheDocument();
  });

  it('renders a number field with the library stepper and bounds', () => {
    render(<TextFieldFds label="Order" type="number" min={0} max={10} defaultValue={3} />);
    const input = screen.getByRole('spinbutton', { name: 'Order' });
    expect(input).toHaveAttribute('min', '0');
    expect(input).toHaveAttribute('max', '10');
    expect(screen.getByRole('button', { name: 'Increase value' })).toBeInTheDocument();
  });

  it('renders multiline as a textarea without a resize handle and keeps rows', () => {
    render(<TextFieldFds label="Description" multiline rows={4} defaultValue="" />);
    const textarea = screen.getByRole('textbox', { name: 'Description' });
    expect(textarea.tagName).toBe('TEXTAREA');
    expect(textarea).toHaveAttribute('rows', '4');
    expect(textarea.className).toContain('resize-none');
  });

  it('puts style on its own root, not on the control', () => {
    render(<TextFieldFds label="Name" style={{ marginTop: 20 }} defaultValue="" />);
    const input = screen.getByRole('textbox', { name: 'Name' });
    expect(input.style.marginTop).toBe('');
    expect((input.closest('div[style]') as HTMLElement).style.marginTop).toBe('20px');
  });
});

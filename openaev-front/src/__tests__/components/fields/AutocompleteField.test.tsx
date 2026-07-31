import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { type ComponentProps, type ReactNode } from 'react';
import { IntlProvider } from 'react-intl';
import { afterEach, describe, expect, it, vi } from 'vitest';

import AutocompleteField from '../../../components/fields/AutocompleteField';
import { type Option } from '../../../utils/Option';

const theme = createTheme();

const OPTIONS: Option[] = [
  {
    id: 'text',
    label: 'Text',
  },
  {
    id: 'number',
    label: 'Number',
  },
  {
    id: 'host',
    label: 'Host',
  },
];

const wrapper = ({ children }: { children: ReactNode }) => (
  <ThemeProvider theme={theme}>
    <IntlProvider locale="en" defaultLocale="en" onError={() => {}}>
      {children}
    </IntlProvider>
  </ThemeProvider>
);

type FieldProps = ComponentProps<typeof AutocompleteField>;

const renderField = (overrides: Partial<FieldProps> = {}) => {
  const props = {
    label: 'Primitive types',
    options: OPTIONS,
    multiple: true,
    value: [] as string[],
    onChange: vi.fn(),
    onInputChange: vi.fn(),
    ...overrides,
  } as FieldProps;
  return render(<AutocompleteField {...props} />, { wrapper });
};

describe('AutocompleteField', () => {
  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
  });

  describe('Option rendering', () => {
    it('renders every option with a checkbox in multiple mode without React key warnings', () => {
      // Arrange
      const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {});

      // Act
      renderField({ open: true });

      // Assert
      const options = screen.getAllByRole('option');
      expect(options).toHaveLength(OPTIONS.length);
      options.forEach(option => expect(option.querySelector('input[type="checkbox"]')).not.toBeNull());
      const keyWarnings = consoleError.mock.calls.filter(call => String(call[0]).includes('key'));
      expect(keyWarnings).toHaveLength(0);
    });

    it('renders options without React key warnings when the tooltip is disabled', () => {
      // Arrange
      const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {});

      // Act
      renderField({
        open: true,
        disableOptionTooltip: true,
      });

      // Assert
      expect(screen.getAllByRole('option')).toHaveLength(OPTIONS.length);
      const keyWarnings = consoleError.mock.calls.filter(call => String(call[0]).includes('key'));
      expect(keyWarnings).toHaveLength(0);
    });

    it('shows a tooltip on option hover by default', async () => {
      // Arrange
      renderField({ open: true });

      // Act
      fireEvent.mouseOver(screen.getAllByRole('option')[0]);

      // Assert
      const tooltip = await screen.findByRole('tooltip');
      expect(tooltip.textContent).toBe(OPTIONS[0].label);
    });

    it('does not show a tooltip on option hover when disableOptionTooltip is set', async () => {
      // Arrange
      renderField({
        open: true,
        disableOptionTooltip: true,
      });

      // Act
      fireEvent.mouseOver(screen.getAllByRole('option')[0]);

      // Assert
      await new Promise(resolve => setTimeout(resolve, 300));
      expect(screen.queryByRole('tooltip')).toBeNull();
    });
  });

  describe('Focus behavior', () => {
    it('focuses the text input on mount when autoFocus is set', () => {
      // Arrange / Act
      renderField({ autoFocus: true });

      // Assert
      expect(document.activeElement).toBe(screen.getByRole('combobox'));
    });

    it('does not steal focus by default', () => {
      // Arrange / Act
      renderField();

      // Assert
      expect(document.activeElement).not.toBe(screen.getByRole('combobox'));
    });
  });

  describe('Multi-selection', () => {
    it('keeps the listbox open across selections when disableCloseOnSelect is set', async () => {
      // Arrange
      const onChange = vi.fn();
      renderField({
        disableCloseOnSelect: true,
        onChange,
      });
      const input = screen.getByRole('combobox');
      fireEvent.keyDown(input, { key: 'ArrowDown' });
      await screen.findByRole('listbox');

      // Act
      fireEvent.click(screen.getAllByRole('option')[0]);

      // Assert
      expect(onChange).toHaveBeenCalledWith([OPTIONS[0].id]);
      await waitFor(() => expect(screen.queryByRole('listbox')).not.toBeNull());
    });
  });
});

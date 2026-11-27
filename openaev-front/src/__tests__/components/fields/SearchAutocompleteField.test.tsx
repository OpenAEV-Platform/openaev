import '@testing-library/jest-dom/vitest';

import { TooltipProvider } from '@filigran/design-system';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { type ComponentProps } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import SearchAutocompleteField from '../../../components/fields/SearchAutocompleteField';
import { type Option } from '../../../utils/Option';

vi.mock('../../../components/i18n', () => ({ useFormatter: () => ({ t: (key: string) => key }) }));

const RED: Option = {
  id: 'red',
  label: 'Red team',
};

const renderField = (props: ComponentProps<typeof SearchAutocompleteField>) => render(
  <TooltipProvider>
    <SearchAutocompleteField {...props} />
  </TooltipProvider>,
);

const pickTheOnlyOption = async () => {
  fireEvent.keyDown(screen.getByRole('combobox'), { key: 'ArrowDown' });
  await screen.findByRole('listbox');
  fireEvent.click(screen.getByRole('option'));
};

describe('SearchAutocompleteField', () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('given_typedText_should_searchWithIt', () => {
    // Arrange
    const onInputChange = vi.fn();
    renderField({
      multiple: true,
      label: 'Teams',
      placeholder: 'Search',
      options: [],
      value: [],
      onChange: vi.fn(),
      onInputChange,
    });
    const input = screen.getByRole('combobox');

    // Act
    fireEvent.change(input, { target: { value: 'red' } });

    // Assert
    expect(input).toHaveAttribute('placeholder', 'Search');
    expect(onInputChange).toHaveBeenCalledWith('red');
  });

  it('given_aChosenIdMissingFromTheOptions_should_keepItWhenPickingAnother', async () => {
    // Arrange
    const onChange = vi.fn();
    renderField({
      multiple: true,
      label: 'Teams',
      options: [RED],
      value: ['gone'],
      onChange,
      onInputChange: vi.fn(),
    });

    // Act
    await pickTheOnlyOption();

    // Assert
    expect(onChange).toHaveBeenCalledWith(['gone', 'red']);
  });

  it('given_aSingleRequiredField_should_giveBackThePickedIdAndShowItsHelp', async () => {
    // Arrange
    const onChange = vi.fn();
    renderField({
      label: 'Team',
      required: true,
      helperText: 'Pick one team',
      options: [RED],
      value: undefined,
      onChange,
      onInputChange: vi.fn(),
    });

    // Act
    await pickTheOnlyOption();

    // Assert
    expect(onChange).toHaveBeenCalledWith('red');
    expect(screen.getByText('Pick one team')).toBeInTheDocument();
  });
});

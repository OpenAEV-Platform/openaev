import { TooltipProvider } from '@filigran/design-system';
import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { type ReactElement } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import DocumentLinkedChips, { type LinkedChipItem } from '../../../../../admin/components/components/documents/DocumentLinkedChips';

const renderChips = (element: ReactElement) => render(
  <ThemeProvider theme={createTheme()}><TooltipProvider>{element}</TooltipProvider></ThemeProvider>,
);

const items: Record<string, LinkedChipItem> = {
  a: {
    id: 'a',
    name: 'Simulation A',
  },
  b: {
    id: 'b',
    name: 'Simulation B',
  },
  c: {
    id: 'c',
    name: 'Simulation C',
  },
};
const resolve = (id: string) => items[id];

// #7696: three stacked chips overflowed the 50 px Documents row into the next one.
describe('DocumentLinkedChips', () => {
  afterEach(cleanup);

  it('shows one chip and counts the others in a +N chip, on a single non-wrapping line', () => {
    const { container } = renderChips(
      <DocumentLinkedChips ids={['a', 'b', 'c', 'd']} resolve={resolve} onOpen={() => {}} />,
    );
    expect(screen.getByText('Simulation A')).toBeTruthy();
    expect(screen.queryByText('Simulation B')).toBeNull();
    expect(screen.getByText('+3')).toBeTruthy();
    const line = container.querySelector('[data-linked-chip]')?.parentElement as HTMLElement;
    const style = getComputedStyle(line);
    expect(style.flexWrap).toBe('nowrap');
    expect(style.overflow).toBe('hidden');
  });

  it('shows no +N chip for a single link', () => {
    renderChips(<DocumentLinkedChips ids={['b']} resolve={resolve} onOpen={() => {}} />);
    expect(screen.getByText('Simulation B')).toBeTruthy();
    expect(screen.queryByText(/^\+/)).toBeNull();
  });

  it('shows a dash when nothing resolves', () => {
    renderChips(<DocumentLinkedChips ids={['x']} resolve={resolve} onOpen={() => {}} />);
    expect(screen.getByText('-')).toBeTruthy();
  });

  it('opens the first linked entity on click', () => {
    const onOpen = vi.fn();
    renderChips(<DocumentLinkedChips ids={['c', 'a']} resolve={resolve} onOpen={onOpen} />);
    fireEvent.click(screen.getByText('Simulation C'));
    expect(onOpen).toHaveBeenCalledWith('c');
  });
});

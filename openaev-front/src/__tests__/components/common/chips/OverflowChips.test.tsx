import '@testing-library/jest-dom/vitest';

import { TooltipProvider } from '@filigran/design-system';
import { cleanup, render, screen } from '@testing-library/react';
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest';

import OverflowChips from '../../../../components/common/chips/OverflowChips';

const CONTAINER_WIDTH = 200;
const CHARACTER_WIDTH = 10;

const originalOffsetWidth = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'offsetWidth');

const renderChips = (labels: string[]) => (
  <TooltipProvider>
    <OverflowChips labels={labels} />
  </TooltipProvider>
);

// The "+N" chip is always mounted: it shows only when chips are hidden.
const isOverflowChipShown = (count: number) => (screen.getByText(`+${count}`).closest('[style*="visibility"]') as HTMLElement).style.visibility === 'visible';

describe('OverflowChips', () => {
  beforeAll(() => {
    if (!('ResizeObserver' in globalThis)) {
      (globalThis as unknown as { ResizeObserver: unknown }).ResizeObserver = class {
        observe() {}

        disconnect() {}
      };
    }
    // The row is 200px wide, a chip as wide as its text: happy-dom lays nothing out.
    Object.defineProperty(HTMLElement.prototype, 'offsetWidth', {
      configurable: true,
      get() {
        const element = this as HTMLElement;
        return element.style.overflow === 'hidden' ? CONTAINER_WIDTH : (element.textContent?.length ?? 0) * CHARACTER_WIDTH;
      },
    });
  });

  afterAll(() => {
    if (originalOffsetWidth) {
      Object.defineProperty(HTMLElement.prototype, 'offsetWidth', originalOffsetWidth);
    }
  });

  afterEach(() => {
    cleanup();
  });

  it('shows every chip that fits on the row', () => {
    // Act
    render(renderChips(['aaaa', 'bbbb']));

    // Assert
    expect(isOverflowChipShown(1)).toBe(false);
  });

  it('collapses the chips that no longer fit when the labels change for as many longer ones', () => {
    // Arrange
    const { rerender } = render(renderChips(['aaaa', 'bbbb']));

    // Act
    rerender(renderChips(['a'.repeat(15), 'b'.repeat(15)]));

    // Assert
    expect(isOverflowChipShown(1)).toBe(true);
  });
});

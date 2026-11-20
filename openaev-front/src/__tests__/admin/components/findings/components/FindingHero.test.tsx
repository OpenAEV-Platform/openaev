import '@testing-library/jest-dom/vitest';

import { cleanup, fireEvent, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import FindingHero from '../../../../../admin/components/findings/components/FindingHero';
import { renderInApp } from '../findingTestUtils';

describe('FindingHero', () => {
  afterEach(cleanup);

  it('should show the title and link the count of findings to the findings page', () => {
    // Arrange + Act
    const { container } = renderInApp(<FindingHero activeFindings={42} viewMode="grid" onViewModeChange={vi.fn()} />);

    // Assert
    expect(screen.getByRole('heading', { name: 'Findings' })).toBeInTheDocument();
    const stat = container.querySelector('a[href="/admin/findings"]');
    expect(stat).toHaveTextContent('42');
  });

  it.each([
    ['grid', 'Grid view', 'List view'],
    ['list', 'List view', 'Grid view'],
  ] as const)('should select the %s button of the given view mode only', (viewMode, selectedName, otherName) => {
    // Arrange + Act
    renderInApp(<FindingHero activeFindings={0} viewMode={viewMode} onViewModeChange={vi.fn()} />);

    // Assert
    expect(screen.getByRole('radiogroup', { name: 'View mode' })).toBeInTheDocument();
    expect(screen.getByRole('radio', { name: selectedName })).toHaveAttribute('aria-checked', 'true');
    expect(screen.getByRole('radio', { name: otherName })).toHaveAttribute('aria-checked', 'false');
  });

  it.each([
    ['grid', 'List view', 'list'],
    ['list', 'Grid view', 'grid'],
  ] as const)('should switch from %s by clicking "%s"', (viewMode, name, expected) => {
    // Arrange
    const onViewModeChange = vi.fn();
    renderInApp(<FindingHero activeFindings={0} viewMode={viewMode} onViewModeChange={onViewModeChange} />);

    // Act
    fireEvent.click(screen.getByRole('radio', { name }));

    // Assert
    expect(onViewModeChange).toHaveBeenCalledWith(expected);
  });
});

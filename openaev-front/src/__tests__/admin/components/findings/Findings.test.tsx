import '@testing-library/jest-dom/vitest';

import { cleanup, fireEvent, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import Findings from '../../../../admin/components/findings/Findings';
import { aFinding, aPage, renderInApp } from './findingTestUtils';

// -- MOCKS --

const mockSearch = vi.fn();

vi.mock('../../../../actions/findings/finding-actions', () => ({ searchDistinctFindings: (input: unknown) => mockSearch(input) }));
vi.mock('../../../../components/common/queryable/pagination/PaginationComponentV2', async () => ({ default: (await import('./findingTestUtils')).PaginationStub }));
vi.mock('../../../../components/common/queryable/useQueryableWithLocalStorage', async () => {
  const { aQueryableHelpers } = await import('./findingTestUtils');
  const queryableHelpers = aQueryableHelpers();
  return {
    useQueryableWithLocalStorage: () => ({
      queryableHelpers,
      searchPaginationInput: {
        page: 0,
        size: 20,
      },
    }),
  };
});
vi.mock('../../../../admin/components/findings/components/FindingSidebar', () => ({ default: () => null }));
// src/components holds an empty, tracked Empty.jsx that Vite resolves before Empty.tsx: stand in for the empty state.
vi.mock('../../../../components/Empty', async () => {
  const { createElement } = await import('react');
  return { default: ({ message }: { message: string }) => createElement('p', null, message) };
});

// -- HELPERS --

const VIEW_MODE_KEY = 'findings:view-mode';

const THREE_FINDINGS = [
  aFinding({ finding_id: 'f-1' }),
  aFinding({ finding_id: 'f-2' }),
  aFinding({ finding_id: 'f-3' }),
];

// -- TESTS --

describe('Findings page', () => {
  beforeEach(() => {
    window.localStorage.clear();
    mockSearch.mockResolvedValue(aPage(THREE_FINDINGS, 42));
  });
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('should show the findings as cards by default, and the number of results in the hero', async () => {
    // Arrange + Act
    const { container } = renderInApp(<Findings />);

    // Assert
    expect(await screen.findAllByTestId('finding-card')).toHaveLength(3);
    expect(screen.queryAllByTestId('finding-row')).toHaveLength(0);
    expect(screen.getByRole('radio', { name: 'Grid view' })).toHaveAttribute('aria-checked', 'true');
    expect(container.querySelector('a[href="/admin/findings"]')).toHaveTextContent('42');
  });

  it('should open on the view mode saved in the local storage', async () => {
    // Arrange
    window.localStorage.setItem(VIEW_MODE_KEY, 'list');

    // Act
    renderInApp(<Findings />);

    // Assert: the list is shown, and it is the selected button
    expect(await screen.findAllByTestId('finding-row')).toHaveLength(3);
    expect(screen.queryAllByTestId('finding-card')).toHaveLength(0);
    expect(screen.getByRole('radio', { name: 'List view' })).toHaveAttribute('aria-checked', 'true');
  });

  it('should switch to the list and remember it', async () => {
    // Arrange
    renderInApp(<Findings />);
    await screen.findAllByTestId('finding-card');

    // Act
    fireEvent.click(screen.getByRole('radio', { name: 'List view' }));

    // Assert
    expect(await screen.findAllByTestId('finding-row')).toHaveLength(3);
    expect(screen.queryAllByTestId('finding-card')).toHaveLength(0);
    expect(window.localStorage.getItem(VIEW_MODE_KEY)).toBe('list');
  });

  it('should say so when there is no finding', async () => {
    // Arrange
    mockSearch.mockResolvedValue(aPage([]));

    // Act
    renderInApp(<Findings />);

    // Assert
    expect(await screen.findByText('No finding found.')).toBeInTheDocument();
  });
});

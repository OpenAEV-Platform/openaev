import '@testing-library/jest-dom/vitest';

import { cleanup, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import FindingList from '../../../../admin/components/findings/FindingList';
import { aFinding, aPage, renderInApp } from './findingTestUtils';

// -- MOCKS --

const mockUseQueryable = vi.fn();

vi.mock('../../../../components/common/queryable/pagination/PaginationComponentV2', async () => ({ default: (await import('./findingTestUtils')).PaginationStub }));
vi.mock('../../../../components/common/queryable/useQueryableWithLocalStorage', async () => {
  const { aQueryableHelpers } = await import('./findingTestUtils');
  const queryableHelpers = aQueryableHelpers();
  return {
    useQueryableWithLocalStorage: (...args: unknown[]) => {
      mockUseQueryable(...args);
      return {
        queryableHelpers,
        searchPaginationInput: {
          page: 0,
          size: 20,
        },
      };
    },
  };
});

// -- HELPERS --

const NOTICE = 'Showing 1 of 5 findings — open the inject to see them all.';

const searchOnePageOfFive = () => vi.fn().mockResolvedValue(aPage([aFinding()], 5));

// -- TESTS --

describe('FindingList', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });
  afterEach(cleanup);

  it('should render its findings through the list view', async () => {
    // Arrange + Act
    renderInApp(<FindingList searchDistinctFindings={searchOnePageOfFive()} filterLocalStorageKey="findings" />);

    // Assert
    expect(await screen.findAllByTestId('finding-row')).toHaveLength(1);
  });

  it('should persist the search under a versioned key', () => {
    // Arrange + Act
    renderInApp(<FindingList searchDistinctFindings={searchOnePageOfFive()} filterLocalStorageKey="atm-findings" />);

    // Assert
    expect(mockUseQueryable.mock.calls[0][0]).toBe('atm-findings-v2');
  });

  it('should hide the given columns', async () => {
    // Arrange + Act
    renderInApp(<FindingList searchDistinctFindings={searchOnePageOfFive()} filterLocalStorageKey="findings" hiddenFields={['finding_created_at']} />);
    await screen.findAllByTestId('finding-row');

    // Assert
    expect(screen.queryByText('First seen')).toBeNull();
    expect(screen.getByText('Last seen')).toBeInTheDocument();
  });

  describe('compact mode', () => {
    it('should ask for a large page and tell when the findings exceed it', async () => {
      // Arrange + Act
      renderInApp(<FindingList searchDistinctFindings={searchOnePageOfFive()} filterLocalStorageKey="findings" compact />);

      // Assert
      expect(await screen.findByText(NOTICE)).toBeInTheDocument();
      expect(mockUseQueryable.mock.calls[0][1]).toMatchObject({ size: 100 });
    });

    it('should not tell anything when all the findings are shown', async () => {
      // Arrange + Act
      renderInApp(<FindingList searchDistinctFindings={vi.fn().mockResolvedValue(aPage([aFinding()]))} filterLocalStorageKey="findings" compact />);
      await screen.findAllByTestId('finding-row');

      // Assert
      expect(screen.queryByText(/Showing/)).toBeNull();
    });
  });

  it('should not tell anything about the total outside of the compact mode', async () => {
    // Arrange + Act
    renderInApp(<FindingList searchDistinctFindings={searchOnePageOfFive()} filterLocalStorageKey="findings" />);
    await screen.findAllByTestId('finding-row');

    // Assert
    expect(screen.queryByText(NOTICE)).toBeNull();
  });
});

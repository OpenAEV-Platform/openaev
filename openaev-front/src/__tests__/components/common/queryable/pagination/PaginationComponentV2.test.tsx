import '@testing-library/jest-dom/vitest';

import { cleanup, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import PaginationComponentV2 from '../../../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../../../components/common/queryable/QueryableUtils';
import { aFinding, aPage, aQueryableHelpers, renderInApp } from '../../../../admin/components/findings/findingTestUtils';

// -- MOCKS --

vi.mock('../../../../../actions/schema/schema-action', () => ({ engineSchemas: vi.fn() }));
vi.mock('../../../../../admin/components/common/filters/useKillChains', () => ({
  default: () => ({
    killChains: [],
    activeKillChain: undefined,
    selectKillChain: vi.fn(),
  }),
}));
vi.mock('../../../../../components/common/queryable/filter/FilterChips', () => ({ default: () => null }));

// -- HELPERS --

const renderPagination = (props: { mainBoxStyle?: React.CSSProperties } = {}) => {
  const finding = aFinding();
  const setContent = vi.fn();
  const { container } = renderInApp(
    <PaginationComponentV2
      fetch={() => Promise.resolve(aPage([finding]))}
      searchPaginationInput={buildSearchPagination({})}
      setContent={setContent}
      queryableHelpers={aQueryableHelpers()}
      searchEnable={false}
      disableFilters
      disablePagination
      {...props}
    />,
  );
  return {
    toolbar: container.firstElementChild as HTMLElement,
    setContent,
    finding,
  };
};

// -- TESTS --

describe('PaginationComponentV2 mainBoxStyle', () => {
  afterEach(cleanup);

  it('should apply the given style to the toolbar row', async () => {
    // Arrange + Act
    const { toolbar, setContent, finding } = renderPagination({ mainBoxStyle: { marginBottom: '8px' } });

    // Assert
    expect(toolbar).toHaveStyle({ marginBottom: '8px' });
    await waitFor(() => expect(setContent).toHaveBeenCalledWith([finding]));
  });

  it('should leave the toolbar row to its stylesheet when no style is given', async () => {
    // Arrange + Act
    const { toolbar, setContent } = renderPagination();

    // Assert
    expect(toolbar).not.toHaveAttribute('style');
    await waitFor(() => expect(setContent).toHaveBeenCalled());
  });
});

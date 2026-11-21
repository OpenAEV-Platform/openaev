/* eslint-disable react-refresh/only-export-components -- shared test helpers, never hot-reloaded */
import { TooltipProvider } from '@filigran/design-system';
import { createTheme, ThemeProvider } from '@mui/material/styles';
import { render } from '@testing-library/react';
import { type ReactElement, useEffect } from 'react';
import { IntlProvider } from 'react-intl';
import { MemoryRouter } from 'react-router';
import { vi } from 'vitest';

import { type Page } from '../../../../components/common/queryable/Page';
import { type QueryableHelpers } from '../../../../components/common/queryable/QueryableHelpers';
import { type AggregatedFindingOutput, type SearchPaginationInput } from '../../../../utils/api-types';

// Real i18n (the keys are the English texts), router, theme and tooltip provider, as in the app.
export const renderInApp = (ui: ReactElement) => render(
  <MemoryRouter>
    <ThemeProvider theme={createTheme()}>
      <TooltipProvider>
        <IntlProvider locale="en" defaultLocale="en" onError={() => {}}>{ui}</IntlProvider>
      </TooltipProvider>
    </ThemeProvider>
  </MemoryRouter>,
);

export const aFinding = (overrides: Partial<AggregatedFindingOutput> = {}): AggregatedFindingOutput => ({
  finding_assets: [],
  finding_created_at: '2026-01-01T10:00:00Z',
  finding_id: 'finding-1',
  finding_type: 'ipv4',
  finding_updated_at: '2026-02-01T10:00:00Z',
  finding_value: '10.0.0.1',
  ...overrides,
});

export const aPage = (content: AggregatedFindingOutput[], totalElements = content.length) => ({
  data: {
    content,
    totalElements,
  } as Page<AggregatedFindingOutput>,
});

export const aQueryableHelpers = (): QueryableHelpers => ({
  textSearchHelpers: { handleTextSearch: vi.fn() },
  paginationHelpers: {
    handleChangePage: vi.fn(),
    handleChangeTotalElements: vi.fn(),
    getTotalElements: vi.fn(() => 0),
  },
  filterHelpers: {
    handleAddFilterWithEmptyValue: vi.fn(),
    handleRemoveFilterById: vi.fn(),
    handleRemoveFilterByKey: vi.fn(),
    handleUpdateValuesById: vi.fn(),
  },
  sortHelpers: {
    handleSort: vi.fn(),
    handleDirectedSort: vi.fn(),
    getSortBy: () => 'finding_updated_at',
    getSortAsc: () => false,
  },
}) as unknown as QueryableHelpers;

interface PaginationStubProps {
  fetch: (input: SearchPaginationInput) => Promise<{ data: Page<AggregatedFindingOutput> }>;
  setContent: (content: AggregatedFindingOutput[]) => void;
}

// Stands for PaginationComponentV2 (filters, schemas, pager): loads the page once, like the real one.
export const PaginationStub = ({ fetch, setContent }: PaginationStubProps) => {
  useEffect(() => {
    fetch({} as SearchPaginationInput)
      .then(res => setContent(res.data.content))
      .catch(() => {});
  }, []);
  return null;
};

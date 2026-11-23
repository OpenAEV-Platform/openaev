import '@testing-library/jest-dom/vitest';

import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { type FilterHelpers } from '../../../../components/common/queryable/filter/FilterHelpers';
import { type SearchPaginationInput } from '../../../../utils/api-types';

vi.mock('../../../../components/i18n', () => ({ useFormatter: () => ({ t: (value: string) => value }) }));
vi.mock('../../../../actions/assets/endpoint-actions', () => ({ searchEndpointLinkedToFindingsAsOption: () => Promise.resolve({ data: [] }) }));
vi.mock('../../../../actions/asset_groups/assetgroup-action', () => ({ searchAssetGroupLinkedToFindingsAsOption: () => Promise.resolve({ data: [] }) }));

import FindingSidebar from '../../../../admin/components/findings/FindingSidebar';

const buildFilterHelpers = () => ({
  handleAddFilterWithEmptyValue: vi.fn(),
  handleUpdateValuesById: vi.fn(),
  handleRemoveFilterById: vi.fn(),
  handleRemoveFilterByKey: vi.fn(),
} as unknown as FilterHelpers);

const renderSidebar = (filterHelpers: FilterHelpers) => render(
  <FindingSidebar
    searchPaginationInput={{
      page: 0,
      size: 20,
    } as SearchPaginationInput}
    filterHelpers={filterHelpers}
  />,
);

describe('FindingSidebar', () => {
  afterEach(cleanup);

  it('given_theSidebar_should_listSeverityFirstThenType', () => {
    // Arrange / Act
    renderSidebar(buildFilterHelpers());

    // Assert
    const sections = screen.getAllByRole('region');
    expect(sections[0]).toHaveAccessibleName('Severity');
    expect(sections[1]).toHaveAccessibleName('Type');
    expect(within(sections[0]).getAllByRole('checkbox').map(row => row.getAttribute('aria-label'))).toEqual(['Critical', 'High', 'Medium', 'Low']);
  });

  it('given_aSeverityTicked_should_addTheSeverityFilter', () => {
    // Arrange
    const filterHelpers = buildFilterHelpers();
    renderSidebar(filterHelpers);

    // Act
    fireEvent.click(screen.getByRole('checkbox', { name: 'Critical' }));

    // Assert
    expect(filterHelpers.handleAddFilterWithEmptyValue).toHaveBeenCalledWith(expect.objectContaining({
      key: 'finding_severity',
      operator: 'eq',
      values: ['CRITICAL'],
    }));
  });
});

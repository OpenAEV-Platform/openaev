import { cleanup, render } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import ThreatArsenalSidebar from '../../../../admin/components/threat_arsenal/ThreatArsenalSidebar';
import { type FacetSection } from '../../../../components/common/facets/FacetFilters';
import { type FilterHelpers } from '../../../../components/common/queryable/filter/FilterHelpers';
import { type SearchPaginationInput } from '../../../../utils/api-types';

const { captured } = vi.hoisted(() => ({ captured: { sections: [] as FacetSection[] } }));

vi.mock('../../../../components/i18n', async importOriginal => ({
  ...(await importOriginal<object>()),
  useFormatter: () => ({ t: (value: string) => value }),
}));

vi.mock('../../../../components/common/facets/FacetFilters', () => ({
  FacetSidebar: ({ sections }: { sections: FacetSection[] }) => {
    captured.sections = sections;
    return null;
  },
}));

const filterHelpers = {
  handleAddFilterWithEmptyValue: vi.fn(),
  handleUpdateValuesById: vi.fn(),
  handleRemoveFilterById: vi.fn(),
  handleRemoveFilterByKey: vi.fn(),
} as unknown as FilterHelpers;

const renderSidebar = () => render(
  <ThreatArsenalSidebar
    domainElements={[]}
    authorOptions={[]}
    facetCounts={{
      platforms: {},
      statuses: {
        VERIFIED: 23,
        DRAFT: 1,
      },
      approvals: {
        APPROVED: 16,
        SUPERSEDED: 2,
      },
    }}
    searchPaginationInput={{} as SearchPaginationInput}
    filterHelpers={filterHelpers}
  />,
);

const rowsOf = (id: string) => captured.sections.find(section => section.id === id)?.rows ?? [];

describe('ThreatArsenalSidebar', () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('given the approval facet should offer only pending approval, approved and rejected', () => {
    // Act
    renderSidebar();

    // Assert
    expect(rowsOf('approval').map(row => row.value)).toEqual(['PENDING', 'APPROVED', 'REJECTED']);
  });

  it('given an action with a draft should show Draft under Status with its count', () => {
    // Act
    renderSidebar();

    // Assert
    const draft = rowsOf('status').find(row => row.value === 'DRAFT');
    expect(draft?.label).toBe('Draft');
    expect(draft?.count).toBe(1);
  });

  it('given Draft ticked should filter on the payload status', () => {
    // Arrange
    renderSidebar();
    const draft = rowsOf('status').find(row => row.value === 'DRAFT');

    // Act
    draft?.onToggle();

    // Assert
    expect(filterHelpers.handleAddFilterWithEmptyValue).toHaveBeenCalledWith(expect.objectContaining({
      key: 'action_payload_status',
      values: ['DRAFT'],
    }));
  });
});

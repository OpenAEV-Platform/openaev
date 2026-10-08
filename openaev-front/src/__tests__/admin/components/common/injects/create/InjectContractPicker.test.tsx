import { TooltipProvider } from '@filigran/design-system';
import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, render } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import InjectContractPicker from '../../../../../../admin/components/common/injects/create/InjectContractPicker';
import { type SearchPaginationInput } from '../../../../../../utils/api-types';
import { DEFAULT_TENANT_UUID } from '../../../../../../utils/url-helper';

// The filters the picker queries with are the ones it hands to its pagination component.
const { paginationInputs } = vi.hoisted(() => ({ paginationInputs: [] as SearchPaginationInput[] }));

vi.mock('../../../../../../components/common/queryable/pagination/PaginationComponentV2', () => ({
  default: ({ searchPaginationInput }: { searchPaginationInput: SearchPaginationInput }) => {
    paginationInputs.push(searchPaginationInput);
    return null;
  },
}));
vi.mock('../../../../../../components/i18n', async importOriginal => ({
  ...(await importOriginal<object>()),
  useFormatter: () => ({
    t: (value: string) => value,
    tPick: () => '',
  }),
}));
vi.mock('../../../../../../store', () => ({
  useHelper: () => ({
    attackPatterns: [],
    attackPatternsMap: {},
    killChainPhasesMap: {},
    domainOptions: [],
  }),
}));
vi.mock('../../../../../../utils/hooks', () => ({ useAppDispatch: () => vi.fn() }));
vi.mock('../../../../../../utils/hooks/useDataLoader', () => ({ default: () => {} }));
vi.mock('../../../../../../actions/InjectorContracts', () => ({ searchInjectorContracts: () => Promise.resolve({ data: { content: [] } }) }));
vi.mock('../../../../../../admin/components/common/domains/useDomainIconFilter', () => ({ default: () => ({ iconBarOrderedDomains: [] }) }));
vi.mock('../../../../../../admin/components/common/injects/create/InjectContractSidebar', () => ({ default: () => null }));
vi.mock('../../../../../../admin/components/common/injects/create/InjectSelectionBar', () => ({ default: () => null }));

// A platform filter left by an earlier use of the picker, under the keys it used to persist.
const STALE_LINUX_FILTER = JSON.stringify({
  page: 0,
  size: 50,
  filterGroup: {
    mode: 'and',
    filters: [{
      id: 'stale',
      key: 'injector_contract_platforms',
      operator: 'contains',
      values: ['Linux'],
      mode: 'or',
    }],
  },
});

const renderPicker = (isAtomic: boolean) => render(
  <MemoryRouter>
    <ThemeProvider theme={createTheme()}>
      <TooltipProvider>
        <InjectContractPicker title="Pick an action" isAtomic={isAtomic} onSelectContract={vi.fn()} />
      </TooltipProvider>
    </ThemeProvider>
  </MemoryRouter>,
);

const filterKeys = () => (paginationInputs.at(-1)?.filterGroup?.filters ?? []).map(filter => filter.key);

describe('InjectContractPicker: opens with no filter left from a previous use', () => {
  beforeEach(() => {
    paginationInputs.length = 0;
    localStorage.setItem(`${DEFAULT_TENANT_UUID}:injector-contracts-picker`, STALE_LINUX_FILTER);
    localStorage.setItem(`${DEFAULT_TENANT_UUID}:injector-contracts-picker-atomic`, STALE_LINUX_FILTER);
  });

  afterEach(() => {
    cleanup();
    localStorage.clear();
  });

  it('starts with no filter when adding an inject to a scenario or simulation', () => {
    // Arrange / Act
    renderPicker(false);

    // Assert
    expect(filterKeys()).toEqual([]);
  });

  it('starts with only its intended default (atomic-capable actions) when creating an atomic testing', () => {
    // Arrange / Act
    renderPicker(true);

    // Assert
    expect(filterKeys()).toEqual(['injector_contract_atomic_testing']);
  });
});

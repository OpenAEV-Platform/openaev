import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { type ReactNode } from 'react';
import { MemoryRouter } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { type ThreatArsenalAction } from '../../../../utils/api-types';

const { state, EMPTY_STATE, DELETE_SELECTION } = vi.hoisted(() => ({
  EMPTY_STATE: 'Empty arsenal',
  DELETE_SELECTION: 'Delete selection',
  state: {
    viewMode: 'grid',
    actions: [] as ThreatArsenalAction[],
    count: 0,
    bulkDelete: vi.fn(),
    olderVersionsInUse: [] as string[],
    reloadOlderVersions: vi.fn(),
  },
}));

vi.mock('../../../../components/i18n', () => ({ useFormatter: () => ({ t: (key: string) => key }) }));
vi.mock('../../../../utils/permissions/permissionsContext', () => ({
  Can: ({ children }: { children: ReactNode }) => <>{children}</>,
  useAbility: () => ({ can: () => true }),
}));
vi.mock('../../../../admin/components/threat_arsenal/hook/useActiveActions', () => ({
  ACTIVE_FILTER_NAMES: [],
  default: () => ({
    threatArsenalActions: state.actions,
    queryableHelpers: {
      paginationHelpers: { handleChangePage: vi.fn() },
      textSearchHelpers: { handleTextSearch: vi.fn() },
    },
    searchPaginationInput: {
      page: 0,
      size: 20,
    },
    totalElements: state.actions.length,
    loading: false,
    hasActiveFilters: false,
    resetFilters: vi.fn(),
    addAction: vi.fn(),
    replaceAction: vi.fn(),
    removeAction: vi.fn(),
  }),
}));
vi.mock('../../../../admin/components/threat_arsenal/hook/useOlderVersionsInUse', () => ({
  default: () => ({
    olderUsages: (actionId: string) => (state.olderVersionsInUse.includes(actionId) ? 1 : 0),
    reload: state.reloadOlderVersions,
  }),
}));
vi.mock('../../../../admin/components/threat_arsenal/hook/useActiveFacets', () => ({ default: () => ({ domainOptions: [] }) }));
vi.mock('../../../../admin/components/threat_arsenal/hook/useActiveSelection', () => ({
  default: () => ({
    count: state.count,
    label: `${state.count} selected`,
    selectAll: false,
    selectedElements: {},
    deSelectedElements: {},
    isOnlyOne: state.count === 1,
    bulkDelete: state.bulkDelete,
  }),
}));
vi.mock('../../../../components/common/marketplace/toolbar/useMarketplaceView', () => ({
  default: () => ({
    viewMode: state.viewMode,
    handleViewModeChange: vi.fn(),
  }),
}));
vi.mock('../../../../components/common/marketplace/page/MarketplaceBrowser', () => ({
  default: ({ toolbar, children }: {
    toolbar: ReactNode;
    children: ReactNode;
  }) => (
    <div>
      {toolbar}
      {children}
    </div>
  ),
}));
vi.mock('../../../../components/common/queryable/pagination/PaginationComponentV2', () => ({ default: ({ leftSlot }: { leftSlot: ReactNode }) => <div>{leftSlot}</div> }));
vi.mock('../../../../components/common/marketplace/results/MarketplaceSections', () => ({
  default: ({ items, emptyState, sections, renderItem }: {
    items: ThreatArsenalAction[];
    emptyState: ReactNode;
    sections: {
      sectionKey: (action: ThreatArsenalAction) => string | undefined;
      sectionLabel: (name: string | undefined) => string;
    };
    renderItem: (action: ThreatArsenalAction) => ReactNode;
  }) => (items.length === 0
    ? emptyState
    : items.map(item => (
        <section key={item.injector_contract_id}>
          <h2>{sections.sectionLabel(sections.sectionKey(item))}</h2>
          {renderItem(item)}
        </section>
      ))),
}));
vi.mock('../../../../admin/components/threat_arsenal/card/ThreatArsenalCard', () => ({
  default: ({ action, olderUsages, onSelect, onRunTest }: {
    action: ThreatArsenalAction;
    olderUsages: number;
    onSelect: () => void;
    onRunTest: () => void;
  }) => (
    <div>
      {olderUsages > 0 && <p>{`Older versions of ${action.injector_contract_id}`}</p>}
      <button type="button" onClick={onSelect}>{`Open ${action.injector_contract_id}`}</button>
      <button type="button" onClick={onRunTest}>{`Test ${action.injector_contract_id}`}</button>
    </div>
  ),
}));
vi.mock('../../../../admin/components/threat_arsenal/bulk/ThreatArsenalSelectAll', () => ({ default: ({ label }: { label: string }) => <span>{label}</span> }));
vi.mock('../../../../admin/components/threat_arsenal/bulk/ThreatArsenalSelectionBar', () => ({
  default: ({ count, onDelete }: {
    count: number;
    onDelete?: () => void;
  }) => (count > 0 ? <button type="button" onClick={onDelete}>{DELETE_SELECTION}</button> : null),
}));
vi.mock('../../../../components/common/DialogDelete', () => ({
  default: ({ open, text, handleSubmit }: {
    open: boolean;
    text: string;
    handleSubmit: () => void;
  }) => (open ? <button type="button" onClick={handleSubmit}>{text}</button> : null),
}));
vi.mock('../../../../admin/components/threat_arsenal/bulk/ThreatArsenalRunTestDrawer', () => ({ default: ({ selectionCount }: { selectionCount: number }) => <p>{`Run a test on ${selectionCount}`}</p> }));
vi.mock('../../../../admin/components/threat_arsenal/ThreatArsenalInformationDrawer', () => ({
  default: ({ threatArsenalAction, onClose }: {
    threatArsenalAction: ThreatArsenalAction;
    onClose: () => void;
  }) => (
    <div>
      <p>{`Drawer of ${threatArsenalAction.injector_contract_id}`}</p>
      <button type="button" aria-label="Close drawer" onClick={onClose} />
    </div>
  ),
}));
vi.mock('../../../../admin/components/threat_arsenal/ThreatArsenalEmptyState', () => ({ default: () => <p>{EMPTY_STATE}</p> }));
vi.mock('../../../../admin/components/threat_arsenal/utils/actionDomains', () => ({ default: () => [] }));
vi.mock('../../../../admin/components/common/domains/DomainsIcons', () => ({ DOMAIN_ICON_MAP: {} }));
vi.mock('../../../../admin/components/threat_arsenal/list/ThreatArsenalList', () => ({ default: () => null }));
vi.mock('../../../../admin/components/threat_arsenal/list/threatArsenalListConfig', () => ({ THREAT_ARSENAL_SORT_OPTIONS: [] }));
vi.mock('../../../../admin/components/threat_arsenal/page/ThreatArsenalHero', () => ({ default: () => null }));
vi.mock('../../../../admin/components/threat_arsenal/page/ThreatArsenalTabs', () => ({ default: () => null }));
vi.mock('../../../../admin/components/threat_arsenal/page/ThreatArsenalSidebar', () => ({ default: () => null }));
vi.mock('../../../../admin/components/threat_arsenal/page/ThreatArsenalHeaderActions', () => ({ default: () => null }));
vi.mock('../../../../admin/components/threat_arsenal/CreateThreatArsenalAction', () => ({ default: () => null }));
vi.mock('../../../../components/common/marketplace/toolbar/MarketplaceViewToggle', () => ({ default: () => null }));
vi.mock('../../../../components/common/marketplace/toolbar/MarketplaceSortSelect', () => ({ default: () => null }));
vi.mock('../../../../components/common/marketplace/results/MarketplaceLoadMore', () => ({ default: () => null }));

import ThreatArsenal from '../../../../admin/components/threat_arsenal/ThreatArsenal';

const action = (id: string): ThreatArsenalAction => ({ injector_contract_id: id } as ThreatArsenalAction);

const renderPage = () => render(<MemoryRouter><ThreatArsenal /></MemoryRouter>);

describe('ThreatArsenal', () => {
  beforeEach(() => {
    state.viewMode = 'grid';
    state.actions = [];
    state.count = 0;
    state.olderVersionsInUse = [];
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('given_noAction_should_showTheEmptyState', () => {
    // Act
    renderPage();

    // Assert
    expect(screen.getByText(EMPTY_STATE)).toBeTruthy();
  });

  it('given_actions_should_openOneAndRunATestOnIt', () => {
    // Arrange
    state.actions = [action('a'), action('b')];
    renderPage();

    // Act
    fireEvent.click(screen.getByRole('button', { name: 'Open b' }));
    fireEvent.click(screen.getByRole('button', { name: 'Test a' }));

    // Assert
    expect(screen.getAllByRole('heading', { name: 'Other' })).toHaveLength(2);
    expect(screen.getByText('Drawer of b')).toBeTruthy();
    expect(screen.getByText('Run a test on 1')).toBeTruthy();
  });

  it('given_injectsOnAnOlderVersion_should_flagTheCardAndRecheckItWhenTheDrawerCloses', () => {
    // Arrange
    const b = action('b');
    state.actions = [action('a'), b];
    state.olderVersionsInUse = ['b'];
    renderPage();

    // Act
    fireEvent.click(screen.getByRole('button', { name: 'Open b' }));
    fireEvent.click(screen.getByRole('button', { name: 'Close drawer' }));

    // Assert
    expect(screen.getByText('Older versions of b')).toBeTruthy();
    expect(screen.queryByText('Older versions of a')).toBeNull();
    expect(state.reloadOlderVersions).toHaveBeenCalledWith(b);
  });

  it('given_theListView_should_offerToSelectAll', () => {
    // Arrange
    state.viewMode = 'list';
    state.count = 3;

    // Act
    renderPage();

    // Assert
    expect(screen.getByText('3 selected')).toBeTruthy();
  });

  it('given_selectedActions_should_deleteThemOnceConfirmed', () => {
    // Arrange
    state.actions = [action('a'), action('b')];
    state.count = 2;
    renderPage();

    // Act
    fireEvent.click(screen.getByRole('button', { name: DELETE_SELECTION }));
    fireEvent.click(screen.getByRole('button', { name: 'Do you want to delete the selected actions? (2)' }));

    // Assert
    expect(state.bulkDelete).toHaveBeenCalledOnce();
    expect(screen.queryByRole('button', { name: 'Do you want to delete the selected actions? (2)' })).toBeNull();
  });
});

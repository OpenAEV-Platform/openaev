import { TooltipProvider } from '@filigran/design-system';
import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { type ReactNode, useEffect } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const {
  mockCan,
  mockExport,
  mockImport,
  mockDownload,
  searchInput,
} = vi.hoisted(() => ({
  mockCan: vi.fn(),
  mockExport: vi.fn(),
  mockImport: vi.fn(),
  mockDownload: vi.fn(),
  searchInput: {
    page: 0,
    size: 20,
    textSearch: 'amber',
  },
}));

vi.mock('../../../../../components/i18n', () => ({
  useFormatter: () => ({
    t: (value: string) => value,
    fldt: (value: string) => value,
  }),
}));

vi.mock('../../../../../utils/utils', () => ({ download: mockDownload }));

// ExportButton reads the tags of the store to translate the exports it builds itself.
vi.mock('../../../../../store', () => ({ useHelper: () => ({ tagsMap: {} }) }));

vi.mock('../../../../../utils/hooks', () => ({ useAppDispatch: () => vi.fn() }));

vi.mock('../../../../../utils/permissions/permissionsContext', () => ({
  Can: ({
    I,
    a,
    children,
  }: {
    I: string;
    a: string;
    children: ReactNode;
  }) => (mockCan(I, a) ? <>{children}</> : null),
}));

vi.mock('../../../../../actions/marking_definitions/marking-definition-actions', () => ({
  createMarkingDefinition: vi.fn(),
  searchMarkingDefinitions: vi.fn(),
  exportMarkingDefinitions: mockExport,
  importMarkingDefinitions: mockImport,
}));

vi.mock('../../../../../components/Breadcrumbs', () => ({ default: () => <div /> }));
vi.mock('../../../../../components/common/ButtonCreate', () => ({ default: () => <button type="button" aria-label="create" /> }));
vi.mock('../../../../../components/common/Drawer', () => ({ default: () => <div /> }));
vi.mock('../../../../../components/common/queryable/sort/SortHeadersComponentV2', () => ({ default: () => <div /> }));
vi.mock('../../../../../components/PaginatedListLoader', () => ({ default: () => <div /> }));
vi.mock('../../../../../admin/components/settings/SecurityMenu', () => ({ default: () => <div /> }));
vi.mock('../../../../../admin/components/settings/marking_definitions/MarkingDefinitionForm', () => ({ default: () => <div /> }));
vi.mock('../../../../../admin/components/settings/marking_definitions/MarkingDefinitionPopover', () => ({ default: () => <div /> }));

vi.mock('../../../../../components/common/queryable/QueryableUtils', () => ({ buildSearchPagination: () => ({}) }));
vi.mock('../../../../../components/common/queryable/style/style', () => ({
  default: () => ({
    bodyItems: {},
    bodyItem: {},
  }),
}));
vi.mock('../../../../../components/common/queryable/useQueryableWithLocalStorage', () => ({
  useQueryableWithLocalStorage: () => ({
    queryableHelpers: {
      sortHelpers: {},
      paginationHelpers: { incrementTotalElements: vi.fn() },
    },
    searchPaginationInput: searchInput,
  }),
}));

// Renders the toolbar buttons, loads one marking definition, and shows how many reloads were asked.
vi.mock('../../../../../components/common/queryable/pagination/PaginationComponentV2', () => ({
  default: ({
    topBarButtons,
    setContent,
    setLoading,
    reloadContentCount,
  }: {
    topBarButtons: ReactNode;
    setContent: (content: unknown[]) => void;
    setLoading: (loading: boolean) => void;
    reloadContentCount: number;
  }) => {
    useEffect(() => {
      setLoading(false);
      setContent([{
        marking_definition_id: 'm1',
        marking_definition_type: 'tlp',
        marking_definition_definition: 'tlp:amber',
        marking_definition_color: '#ffc107',
        marking_definition_order: 3,
        marking_definition_protected: false,
        marking_definition_created_at: '2026-01-01T00:00:00Z',
      }]);
      // Loaded once, as the real component does on mount.
    }, []);
    return (
      <div>
        <span data-testid="reload-count">{reloadContentCount}</span>
        {topBarButtons}
      </div>
    );
  },
}));

import MarkingDefinitions from '../../../../../admin/components/settings/marking_definitions/MarkingDefinitions';

const renderPage = () => render(<ThemeProvider theme={createTheme()}><TooltipProvider><MarkingDefinitions /></TooltipProvider></ThemeProvider>);

const allow = (...granted: string[]) => mockCan.mockImplementation((action: string, subject: string) => granted.includes(`${action} ${subject}`));

const csvFile = () => new File(['type,definition,color,order\r\nTLP,TLP:AMBER,#ffc107,3'], 'markings.csv', { type: 'text/csv' });

describe('MarkingDefinitions import and export', () => {
  beforeEach(() => {
    mockExport.mockResolvedValue({
      data: 'type,definition,color,order\r\n',
      filename: 'MarkingDefinitions202611191200.csv',
    });
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('shows the export to a read-only user, and no import', () => {
    // Arrange
    allow('ACCESS MARKING_DEFINITION');

    // Act
    renderPage();

    // Assert
    expect(screen.getByRole('button', { name: 'Export this list' })).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Import marking definitions' })).toBeNull();
  });

  it('shows the import and the export to a user who can manage', () => {
    // Arrange
    allow('ACCESS MARKING_DEFINITION', 'MANAGE MARKING_DEFINITION');

    // Act
    renderPage();

    // Assert
    expect(screen.getByRole('button', { name: 'Export this list' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Import marking definitions' })).toBeTruthy();
  });

  it('shows neither without the capability', () => {
    // Arrange
    allow();

    // Act
    renderPage();

    // Assert
    expect(screen.queryByRole('button', { name: 'Export this list' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Import marking definitions' })).toBeNull();
  });

  it('exports what the list currently shows and downloads it as CSV', async () => {
    // Arrange
    allow('ACCESS MARKING_DEFINITION');
    renderPage();

    // Act
    fireEvent.click(screen.getByRole('button', { name: 'Export this list' }));

    // Assert
    await waitFor(() => expect(mockDownload).toHaveBeenCalledWith('type,definition,color,order\r\n', 'MarkingDefinitions202611191200.csv', 'text/csv'));
    expect(mockExport).toHaveBeenCalledWith(searchInput);
  });

  it('accepts only CSV files for the import', () => {
    // Arrange
    allow('MANAGE MARKING_DEFINITION');

    // Act
    renderPage();

    // Assert
    expect(document.querySelector('input[type="file"]')?.getAttribute('accept')).toBe('.csv');
  });

  it('reloads the list after an import that created something, and not after one that created nothing', async () => {
    // Arrange
    allow('MANAGE MARKING_DEFINITION');
    renderPage();
    const input = document.querySelector('input[type="file"]') as HTMLInputElement;
    const report = (created: number) => ({
      import_created: created,
      import_skipped: 0,
      import_ignored: 0,
      import_columns: [],
      import_rows: [],
    });

    // Act / Assert
    mockImport.mockResolvedValueOnce(report(0));
    fireEvent.change(input, { target: { files: [csvFile()] } });
    await waitFor(() => expect(mockImport).toHaveBeenCalledTimes(1));
    expect(screen.getByTestId('reload-count').textContent).toBe('0');

    mockImport.mockResolvedValueOnce(report(1));
    fireEvent.change(input, { target: { files: [csvFile()] } });
    await waitFor(() => expect(screen.getByTestId('reload-count').textContent).toBe('1'));
  });
});

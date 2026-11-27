import { beforeEach, describe, expect, it, vi } from 'vitest';

const { simpleCall, simplePostCall } = vi.hoisted(() => ({
  simpleCall: vi.fn((_uri?: string, _config?: unknown) => Promise.resolve({ data: [] as unknown })),
  simplePostCall: vi.fn((_uri?: string, _data?: unknown, _config?: unknown, _notifyError?: boolean) => Promise.resolve({ data: { content: [] as unknown[] } as unknown })),
}));
vi.mock('../../../../utils/Action', async importOriginal => ({
  ...(await importOriginal<Record<string, unknown>>()),
  simpleCall,
  simplePostCall,
}));

const { searchSubjectOptions, SUBJECT_OPTIONS_SIZE } = await import('../../../../admin/components/reporting/ReportingFormUtils');

const expectedSearch = (textSearch: string, property: string) => ({
  page: 0,
  size: SUBJECT_OPTIONS_SIZE,
  textSearch,
  sorts: [{
    property,
    direction: 'ASC',
  }],
});

describe('searchSubjectOptions', () => {
  beforeEach(() => {
    simpleCall.mockClear();
    simplePostCall.mockClear();
  });

  it('lists every readable asset group through the asset group search, not the inject-scoped options helper', async () => {
    // Arrange
    simplePostCall.mockResolvedValueOnce({
      data: {
        content: [
          {
            asset_group_id: 'group-1',
            asset_group_name: 'OVHCloud Servers',
          },
          {
            asset_group_id: 'group-2',
            asset_group_name: 'filigran.io external hosts',
          },
        ],
      },
    });

    // Act
    const options = await searchSubjectOptions('ASSET_GROUP', 'servers');

    // Assert
    expect(simplePostCall).toHaveBeenCalledWith('/api/asset_groups/search', expectedSearch('servers', 'asset_group_name'));
    expect(simpleCall).not.toHaveBeenCalled();
    expect(options).toEqual([
      {
        id: 'group-1',
        label: 'OVHCloud Servers',
      },
      {
        id: 'group-2',
        label: 'filigran.io external hosts',
      },
    ]);
  });

  it('answers no option when the asset group search answers no page content', async () => {
    // Arrange
    simplePostCall.mockResolvedValueOnce({ data: {} });

    // Act
    const options = await searchSubjectOptions('ASSET_GROUP');

    // Assert
    expect(simplePostCall).toHaveBeenCalledWith('/api/asset_groups/search', expectedSearch('', 'asset_group_name'));
    expect(options).toEqual([]);
  });

  it('lists endpoints through the endpoint search', async () => {
    // Arrange
    simplePostCall.mockResolvedValueOnce({
      data: {
        content: [{
          asset_id: 'endpoint-1',
          asset_name: 'web-01',
        }],
      },
    });

    // Act
    const options = await searchSubjectOptions('ENDPOINT', 'web');

    // Assert
    expect(simplePostCall).toHaveBeenCalledWith('/api/endpoints/search', expectedSearch('web', 'asset_name'));
    expect(options).toEqual([{
      id: 'endpoint-1',
      label: 'web-01',
    }]);
  });

  it('lists teams through the team search', async () => {
    // Arrange
    simplePostCall.mockResolvedValueOnce({
      data: {
        content: [{
          team_id: 'team-1',
          team_name: 'SOC',
        }],
      },
    });

    // Act
    const options = await searchSubjectOptions('TEAM', 'so');

    // Assert
    expect(simplePostCall).toHaveBeenCalledWith('/api/teams/search', expectedSearch('so', 'team_name'));
    expect(options).toEqual([{
      id: 'team-1',
      label: 'SOC',
    }]);
  });

  it('keeps the unscoped player options helper', async () => {
    // Arrange
    simpleCall.mockResolvedValueOnce({
      data: [{
        id: 'player-1',
        label: 'Jane',
      }],
    });

    // Act
    const options = await searchSubjectOptions('PLAYER', 'ja');

    // Assert
    expect(simpleCall).toHaveBeenCalledWith('/api/players/options', { params: { searchText: 'ja' } });
    expect(options).toEqual([{
      id: 'player-1',
      label: 'Jane',
    }]);
  });
});

import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { NO_TARGETS, type ScenarioTargets } from '../../../../../admin/components/scenarios/targets/scenarioTargets';
import { type Option } from '../../../../../utils/Option';

interface FieldProps {
  label: string;
  options: Option[];
  value: string[];
  onChange: (ids: string[]) => void;
  onInputChange: (text: string) => void;
}

const mocks = vi.hoisted(() => ({
  searchEndpoints: vi.fn(),
  searchAssetGroups: vi.fn(),
  searchTeams: vi.fn(),
  fields: {} as Record<string, FieldProps>,
}));

vi.mock('../../../../../components/i18n', () => ({ useFormatter: () => ({ t: (key: string) => key }) }));
vi.mock('../../../../../actions/assets/endpoint-actions', () => ({ searchEndpoints: mocks.searchEndpoints }));
vi.mock('../../../../../actions/asset_groups/assetgroup-action', () => ({ searchAssetGroups: mocks.searchAssetGroups }));
vi.mock('../../../../../actions/teams/team-actions', () => ({ searchTeams: mocks.searchTeams }));
vi.mock('../../../../../components/fields/SearchAutocompleteField', () => ({
  default: (props: FieldProps) => {
    mocks.fields[props.label] = props;
    return null;
  },
}));

const { default: ScenarioTargetsFields } = await import('../../../../../admin/components/scenarios/targets/ScenarioTargetsFields');

const WEB: Option = {
  id: 'web',
  label: 'Web server',
};
const ASSETS = 'Assets and asset groups';
const TEAMS = 'Teams and players';
const page = <T, >(content: T[]) => Promise.resolve({ data: { content } });

describe('ScenarioTargetsFields', () => {
  beforeEach(() => {
    mocks.searchEndpoints.mockImplementation(() => page([{
      asset_id: 'db',
      asset_name: 'Database',
    }]));
    mocks.searchAssetGroups.mockImplementation(() => page([]));
    mocks.searchTeams.mockImplementation(() => page([{
      team_id: 'red',
      team_name: 'Red team',
    }]));
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
    mocks.fields = {};
  });

  it('given_aChosenAssetMissingFromTheSearch_should_keepItAmongTheOptions', async () => {
    // Arrange
    const value: ScenarioTargets = {
      ...NO_TARGETS,
      assets: [WEB],
    };

    // Act
    render(<ScenarioTargetsFields intro="" value={value} onChange={vi.fn()} />);

    // Assert
    await waitFor(() => expect(mocks.fields[ASSETS].options).toEqual([{
      id: 'asset:web',
      label: 'Web server',
    }, {
      id: 'asset:db',
      label: 'Database',
    }]));
    expect(mocks.fields[ASSETS].value).toEqual(['asset:web']);
    expect(mocks.searchEndpoints).toHaveBeenCalledWith({
      page: 0,
      size: 50,
      textSearch: '',
    });
  });

  it('given_typedText_should_searchWithIt', async () => {
    // Arrange
    render(<ScenarioTargetsFields intro="" value={NO_TARGETS} onChange={vi.fn()} />);
    await waitFor(() => expect(mocks.fields[TEAMS].options).toHaveLength(1));

    // Act
    act(() => mocks.fields[TEAMS].onInputChange('red'));

    // Assert
    await waitFor(() => expect(mocks.searchTeams).toHaveBeenLastCalledWith({
      page: 0,
      size: 50,
      textSearch: 'red',
    }));
  });

  it('given_aPickedTeam_should_giveBackItsOption', async () => {
    // Arrange
    const onChange = vi.fn();
    render(<ScenarioTargetsFields intro="" value={NO_TARGETS} onChange={onChange} />);
    await waitFor(() => expect(mocks.fields[TEAMS].options).toHaveLength(1));

    // Act
    act(() => mocks.fields[TEAMS].onChange(['red']));

    // Assert
    expect(onChange).toHaveBeenCalledWith({
      ...NO_TARGETS,
      teams: [{
        id: 'red',
        label: 'Red team',
      }],
    });
  });

  it('given_anAssetAndAGroupPicked_should_splitThemByKind', async () => {
    // Arrange
    mocks.searchAssetGroups.mockImplementation(() => page([{
      asset_group_id: 'dmz',
      asset_group_name: 'DMZ',
    }]));
    const onChange = vi.fn();
    render(<ScenarioTargetsFields intro="" value={NO_TARGETS} onChange={onChange} />);
    await waitFor(() => expect(mocks.fields[ASSETS].options).toHaveLength(2));

    // Act
    act(() => mocks.fields[ASSETS].onChange(['asset-group:dmz', 'asset:db']));

    // Assert
    expect(onChange).toHaveBeenCalledWith({
      ...NO_TARGETS,
      assets: [{
        id: 'db',
        label: 'Database',
      }],
      assetGroups: [{
        id: 'dmz',
        label: 'DMZ',
      }],
    });
  });

  it('given_injectsTakingTeamsOnly_should_showTheTeamsFieldOnly', () => {
    // Act
    render(<ScenarioTargetsFields intro="" value={NO_TARGETS} onChange={vi.fn()} kinds={['TEAMS']} />);

    // Assert
    expect(Object.keys(mocks.fields)).toEqual([TEAMS]);
    expect(screen.queryByRole('textbox', { name: 'Manual targets' })).toBeNull();
  });

  it('given_manualTargets_should_giveBackTheText', () => {
    // Arrange
    const onChange = vi.fn();
    render(<ScenarioTargetsFields intro="" value={NO_TARGETS} onChange={onChange} />);

    // Act
    fireEvent.change(screen.getByRole('textbox', { name: 'Manual targets' }), { target: { value: '10.0.0.1' } });

    // Assert
    expect(onChange).toHaveBeenCalledWith({
      ...NO_TARGETS,
      manual: '10.0.0.1',
    });
  });
});

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
  });

  it('given_aChosenAssetMissingFromTheSearch_should_keepItAmongTheOptions', async () => {
    // Arrange
    const value: ScenarioTargets = {
      ...NO_TARGETS,
      assets: [WEB],
    };

    // Act
    render(<ScenarioTargetsFields value={value} onChange={vi.fn()} />);

    // Assert
    await waitFor(() => expect(mocks.fields.Assets.options).toEqual([WEB, {
      id: 'db',
      label: 'Database',
    }]));
    expect(mocks.fields.Assets.value).toEqual(['web']);
    expect(mocks.searchEndpoints).toHaveBeenCalledWith({
      page: 0,
      size: 50,
      textSearch: '',
    });
  });

  it('given_typedText_should_searchWithIt', async () => {
    // Arrange
    render(<ScenarioTargetsFields value={NO_TARGETS} onChange={vi.fn()} />);
    await waitFor(() => expect(mocks.fields.Teams.options).toHaveLength(1));

    // Act
    act(() => mocks.fields.Teams.onInputChange('red'));

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
    render(<ScenarioTargetsFields value={NO_TARGETS} onChange={onChange} />);
    await waitFor(() => expect(mocks.fields.Teams.options).toHaveLength(1));

    // Act
    act(() => mocks.fields.Teams.onChange(['red']));

    // Assert
    expect(onChange).toHaveBeenCalledWith({
      ...NO_TARGETS,
      teams: [{
        id: 'red',
        label: 'Red team',
      }],
    });
  });

  it('given_manualTargets_should_giveBackTheText', () => {
    // Arrange
    const onChange = vi.fn();
    render(<ScenarioTargetsFields value={NO_TARGETS} onChange={onChange} />);

    // Act
    fireEvent.change(screen.getByRole('textbox', { name: 'Manual targets' }), { target: { value: '10.0.0.1' } });

    // Assert
    expect(onChange).toHaveBeenCalledWith({
      ...NO_TARGETS,
      manual: '10.0.0.1',
    });
  });
});

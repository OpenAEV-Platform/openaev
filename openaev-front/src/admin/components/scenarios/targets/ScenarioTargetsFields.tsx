import { Text } from '@filigran/design-system';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useEffect, useState } from 'react';

import { searchAssetGroups } from '../../../../actions/asset_groups/assetgroup-action';
import { searchEndpoints } from '../../../../actions/assets/endpoint-actions';
import { searchTeams } from '../../../../actions/teams/team-actions';
import SearchAutocompleteField from '../../../../components/fields/SearchAutocompleteField';
import TextFieldFds from '../../../../components/fields/TextFieldFds';
import { useFormatter } from '../../../../components/i18n';
import {
  type AssetGroupOutput,
  type EndpointOutput,
  type SearchPaginationInput,
  type TeamOutput,
} from '../../../../utils/api-types';
import { type Option } from '../../../../utils/Option';
import { type ScenarioTargets } from './scenarioTargets';

// Every target of the tenant, not only those already used: the first page of the search by name.
const searchAsOptions = <T, >(
  search: (input: SearchPaginationInput) => Promise<{ data: { content: T[] } }>,
  toOption: (item: T) => Option,
) => (text: string) => search({
  page: 0,
  size: 50,
  textSearch: text,
}).then(({ data }) => data.content.map(toOption));

const searchAssetOptions = searchAsOptions<EndpointOutput>(searchEndpoints, endpoint => ({
  id: endpoint.asset_id,
  label: endpoint.asset_name,
}));
const searchAssetGroupOptions = searchAsOptions<AssetGroupOutput>(searchAssetGroups, group => ({
  id: group.asset_group_id,
  label: group.asset_group_name,
}));
const searchTeamOptions = searchAsOptions<TeamOutput>(searchTeams, team => ({
  id: team.team_id,
  label: team.team_name,
}));

interface TargetFieldProps {
  label: string;
  value: Option[];
  onChange: (value: Option[]) => void;
  search: (text: string) => Promise<Option[]>;
}

const TargetField: FunctionComponent<TargetFieldProps> = ({ label, value, onChange, search }) => {
  const { t } = useFormatter();
  const [found, setFound] = useState<Option[]>([]);
  const [text, setText] = useState('');

  useEffect(() => {
    search(text).then(setFound).catch(() => setFound([]));
  }, [text]);

  // The chosen targets stay among the options, so their chips keep their names whatever the search finds.
  const options = [...value, ...found.filter(option => !value.some(chosen => chosen.id === option.id))];
  return (
    <SearchAutocompleteField
      multiple
      label={label}
      placeholder={t('Search')}
      options={options}
      value={value.map(option => option.id)}
      onChange={(ids: string[]) => onChange(ids.flatMap(id => options.filter(option => option.id === id)))}
      onInputChange={setText}
    />
  );
};

interface Props {
  value: ScenarioTargets;
  onChange: (value: ScenarioTargets) => void;
}

// Each target goes to every inject that takes its kind; the targets already on the injects stay.
const ScenarioTargetsFields: FunctionComponent<Props> = ({ value, onChange }) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const change = (field: Partial<ScenarioTargets>) => onChange({
    ...value,
    ...field,
  });
  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      gap: theme.spacing(2),
    }}
    >
      <Text variant="content-caption">{t('Each target is added to every inject that takes its kind; the targets already set stay.')}</Text>
      <TargetField label={t('Assets')} value={value.assets} onChange={assets => change({ assets })} search={searchAssetOptions} />
      <TargetField label={t('Asset groups')} value={value.assetGroups} onChange={assetGroups => change({ assetGroups })} search={searchAssetGroupOptions} />
      <TargetField label={t('Teams')} value={value.teams} onChange={teams => change({ teams })} search={searchTeamOptions} />
      <TextFieldFds
        label={t('Manual targets')}
        placeholder="10.0.0.12, 10.20.0.0/24, app.example.com"
        value={value.manual}
        onChange={event => change({ manual: event.target.value })}
      />
    </div>
  );
};

export default ScenarioTargetsFields;

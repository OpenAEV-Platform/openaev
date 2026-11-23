import {
  Combobox,
  ComboboxChips,
  ComboboxClear,
  ComboboxContent,
  ComboboxControls,
  ComboboxField,
  ComboboxInput,
  ComboboxLabel,
  ComboboxTrigger,
  Input,
  Text,
} from '@filigran/design-system';
import { type FunctionComponent, useEffect, useState } from 'react';

import { searchAssetGroups } from '../../../../actions/asset_groups/assetgroup-action';
import { searchEndpoints } from '../../../../actions/assets/endpoint-actions';
import { searchTeams } from '../../../../actions/teams/team-actions';
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
  const [options, setOptions] = useState<Option[]>([]);
  const [inputValue, setInputValue] = useState('');

  useEffect(() => {
    search(inputValue).then(setOptions).catch(() => setOptions([]));
  }, [inputValue]);

  return (
    <Combobox<Option>
      multiple
      options={options}
      value={value}
      getOptionLabel={option => option.label}
      isOptionEqualToValue={(option, other) => option.id === other.id}
      inputValue={inputValue}
      onInputChange={(text, meta) => {
        if (meta.cause === 'type') {
          setInputValue(text);
        }
      }}
      onValueChange={next => onChange(next as Option[])}
    >
      <ComboboxLabel>{label}</ComboboxLabel>
      <ComboboxField>
        <ComboboxChips />
        <ComboboxInput placeholder={t('Search')} />
        <ComboboxControls>
          <ComboboxClear />
          <ComboboxTrigger />
        </ComboboxControls>
      </ComboboxField>
      <ComboboxContent emptyMessage={t('No available options')} />
    </Combobox>
  );
};

interface Props {
  value: ScenarioTargets;
  onChange: (value: ScenarioTargets) => void;
}

// Each target goes to every inject that takes its kind; the targets already on the injects stay.
const ScenarioTargetsFields: FunctionComponent<Props> = ({ value, onChange }) => {
  const { t } = useFormatter();
  const change = (field: Partial<ScenarioTargets>) => onChange({
    ...value,
    ...field,
  });
  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      gap: 16,
    }}
    >
      <Text variant="content-caption">{t('Each target is added to every inject that takes its kind; the targets already set stay.')}</Text>
      <TargetField label={t('Assets')} value={value.assets} onChange={assets => change({ assets })} search={searchAssetOptions} />
      <TargetField label={t('Asset groups')} value={value.assetGroups} onChange={assetGroups => change({ assetGroups })} search={searchAssetGroupOptions} />
      <TargetField label={t('Teams')} value={value.teams} onChange={teams => change({ teams })} search={searchTeamOptions} />
      <div style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 4,
      }}
      >
        <Text variant="content-compact">{t('Manual targets')}</Text>
        <Input
          aria-label={t('Manual targets')}
          placeholder="10.0.0.12, 10.20.0.0/24, app.example.com"
          value={value.manual}
          onChange={event => change({ manual: event.target.value })}
        />
      </div>
    </div>
  );
};

export default ScenarioTargetsFields;

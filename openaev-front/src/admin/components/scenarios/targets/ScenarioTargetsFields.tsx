import { Input, Text } from '@filigran/design-system';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, type ReactNode, useEffect, useState } from 'react';

import { searchAssetGroups } from '../../../../actions/asset_groups/assetgroup-action';
import { searchEndpoints } from '../../../../actions/assets/endpoint-actions';
import { searchTeams } from '../../../../actions/teams/team-actions';
import SearchAutocompleteField from '../../../../components/fields/SearchAutocompleteField';
import { useFormatter } from '../../../../components/i18n';
import {
  type AssetGroupOutput,
  type EndpointOutput,
  type SearchPaginationInput,
  type TeamOutput,
} from '../../../../utils/api-types';
import { type Option } from '../../../../utils/Option';
import { type ScenarioTargets, type TargetKind } from './scenarioTargets';
import TargetKindIcon from './TargetKindIcon';
import TargetsGroup from './TargetsGroup';

// Every target of the tenant, not only those already used: the first page of the search by name.
const searchAsOptions = <T, >(
  search: (input: SearchPaginationInput) => Promise<{ data: { content: T[] } }>,
  toOption: (item: T) => Option,
) => (text: string) => search({
  page: 0,
  size: 50,
  textSearch: text,
}).then(({ data }) => data.content.map(toOption));

// Assets and asset groups share one field: their ids carry their kind.
const ASSET = 'asset:';
const ASSET_GROUP = 'asset-group:';

const searchAssetOptions = searchAsOptions<EndpointOutput>(searchEndpoints, endpoint => ({
  id: ASSET + endpoint.asset_id,
  label: endpoint.asset_name,
}));
const searchAssetGroupOptions = searchAsOptions<AssetGroupOutput>(searchAssetGroups, group => ({
  id: ASSET_GROUP + group.asset_group_id,
  label: group.asset_group_name,
}));
const searchAssetAndGroupOptions = (text: string) => Promise.all([searchAssetGroupOptions(text), searchAssetOptions(text)])
  .then(([groups, assets]) => [...groups, ...assets]);
const searchTeamOptions = searchAsOptions<TeamOutput>(searchTeams, team => ({
  id: team.team_id,
  label: team.team_name,
}));

const tagged = (kind: string, options: Option[]) => options.map(option => ({
  ...option,
  id: kind + option.id,
}));
const ofKind = (kind: string, options: Option[]) => options
  .filter(option => option.id.startsWith(kind))
  .map(option => ({
    ...option,
    id: option.id.substring(kind.length),
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
      labelPosition="none"
      placeholder={t('Search')}
      options={options}
      value={value.map(option => option.id)}
      onChange={(ids: string[]) => onChange(ids.flatMap(id => options.filter(option => option.id === id)))}
      onInputChange={setText}
    />
  );
};

interface TargetRowProps {
  kind: TargetKind;
  title: string;
  subtitle: string;
  children: ReactNode;
}

// Label and field on the same grid in every row, so the fields line up.
const TargetRow: FunctionComponent<TargetRowProps> = ({ kind, title, subtitle, children }) => {
  const theme = useTheme();
  return (
    <div style={{
      display: 'grid',
      gridTemplateColumns: `${theme.spacing(2.5)} minmax(0, 1fr) minmax(0, 1.4fr)`,
      alignItems: 'center',
      gap: theme.spacing(1.5),
    }}
    >
      <TargetKindIcon kind={kind} />
      <div>
        <Text variant="content-base-bold">{title}</Text>
        <Text variant="content-caption" className="text-default-secondary">{subtitle}</Text>
      </div>
      {children}
    </div>
  );
};

interface Props {
  // How the chosen targets are applied, above the fields.
  intro: string;
  value: ScenarioTargets;
  onChange: (value: ScenarioTargets) => void;
  // The kinds the injects take; every kind when they are not known yet.
  kinds?: TargetKind[];
}

// Each target goes to every inject that takes its kind; the targets already on the injects stay.
const ScenarioTargetsFields: FunctionComponent<Props> = ({ intro, value, onChange, kinds }) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const takes = (...wanted: TargetKind[]) => !kinds || wanted.some(kind => kinds.includes(kind));
  const change = (field: Partial<ScenarioTargets>) => onChange({
    ...value,
    ...field,
  });
  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      gap: theme.spacing(1),
    }}
    >
      <Text variant="content-caption" className="text-default-secondary">{intro}</Text>
      <TargetsGroup>
        {takes('ASSETS', 'ASSET_GROUPS') && (
          <TargetRow key="assets" kind="ASSETS" title={t('Assets and asset groups')} subtitle={t('Endpoint injects')}>
            <TargetField
              label={t('Assets and asset groups')}
              value={[...tagged(ASSET_GROUP, value.assetGroups), ...tagged(ASSET, value.assets)]}
              onChange={chosen => change({
                assets: ofKind(ASSET, chosen),
                assetGroups: ofKind(ASSET_GROUP, chosen),
              })}
              search={searchAssetAndGroupOptions}
            />
          </TargetRow>
        )}
        {takes('HOSTS') && (
          <TargetRow key="manual" kind="HOSTS" title={t('Manual targets')} subtitle={t('Injects in manual targeting')}>
            <Input
              aria-label={t('Manual targets')}
              placeholder="10.0.0.12, 10.20.0.0/24, app.example.com"
              value={value.manual}
              onChange={event => change({ manual: event.target.value })}
            />
          </TargetRow>
        )}
        {takes('TEAMS') && (
          <TargetRow key="teams" kind="TEAMS" title={t('Teams and players')} subtitle={t('Human injects: mail, SMS, challenges')}>
            <TargetField
              label={t('Teams and players')}
              value={value.teams}
              onChange={teams => change({ teams })}
              search={searchTeamOptions}
            />
          </TargetRow>
        )}
      </TargetsGroup>
    </div>
  );
};

export default ScenarioTargetsFields;

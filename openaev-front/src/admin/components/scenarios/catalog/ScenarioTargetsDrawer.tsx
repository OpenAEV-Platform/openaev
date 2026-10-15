import {
  Button,
  Chip,
  Combobox,
  ComboboxChips,
  ComboboxClear,
  ComboboxContent,
  ComboboxControls,
  ComboboxField,
  ComboboxInput,
  ComboboxLabel,
  ComboboxTrigger,
  Spinner,
  Text,
} from '@filigran/design-system';
import { type FunctionComponent, useEffect, useState } from 'react';

import { searchAssetGroups } from '../../../../actions/asset_groups/assetgroup-action';
import { searchEndpoints } from '../../../../actions/assets/endpoint-actions';
import { searchTeams } from '../../../../actions/teams/team-actions';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import {
  type AssetGroupOutput,
  type CatalogInstallInput,
  type EndpointOutput,
  type InjectPreview,
  type SearchPaginationInput,
  type TeamOutput,
} from '../../../../utils/api-types';
import { type Option } from '../../../../utils/Option';

// Every target of the tenant, not only those already used: first page of the search by name.
const searchAsOptions = <T, >(
  search: (input: SearchPaginationInput) => Promise<{ data: { content: T[] } }>,
  toOption: (item: T) => Option,
) => (text: string) => search({
  page: 0,
  size: 50,
  textSearch: text,
}).then(({ data }) => ({ data: data.content.map(toOption) }));

const searchEndpointOptions = searchAsOptions<EndpointOutput>(searchEndpoints, endpoint => ({
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
  search: (text: string) => Promise<{ data: Option[] }>;
}

const TargetField: FunctionComponent<TargetFieldProps> = ({ label, value, onChange, search }) => {
  const { t } = useFormatter();
  const [options, setOptions] = useState<Option[]>([]);
  const [inputValue, setInputValue] = useState('');

  useEffect(() => {
    search(inputValue).then(({ data }) => setOptions(data));
  }, [inputValue]);

  return (
    <Combobox<Option>
      multiple
      options={options}
      value={value}
      getOptionLabel={option => option.label ?? ''}
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
        <ComboboxInput />
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
  title: string;
  submitLabel: string;
  load: () => Promise<{
    injects: InjectPreview[];
    previousVersion?: string;
  }>;
  onSubmit: (input: CatalogInstallInput) => Promise<unknown>;
  onClose: () => void;
}

// Targets are mandatory (F12): injects without one get those chosen here.
const ScenarioTargetsDrawer: FunctionComponent<Props> = ({ title, submitLabel, load, onSubmit, onClose }) => {
  const { t } = useFormatter();
  const [injects, setInjects] = useState<InjectPreview[] | null>(null);
  const [previousVersion, setPreviousVersion] = useState<string | undefined>();
  const [assets, setAssets] = useState<Option[]>([]);
  const [assetGroups, setAssetGroups] = useState<Option[]>([]);
  const [teams, setTeams] = useState<Option[]>([]);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    load().then((loaded) => {
      setInjects(loaded.injects);
      setPreviousVersion(loaded.previousVersion);
    });
  }, []);

  const neededFields = new Set((injects ?? []).filter(inject => !inject.is_targeted).flatMap(inject => inject.target_fields));
  const isTargeted = (fields: string[]) => (fields.includes('assets') && assets.length > 0)
    || (fields.includes('asset_groups') && assetGroups.length > 0)
    || (fields.includes('teams') && teams.length > 0);
  const missing = (injects ?? []).filter(inject => !inject.is_targeted && !isTargeted(inject.target_fields));

  const submit = () => {
    setSubmitting(true);
    onSubmit({
      asset_ids: assets.map(o => o.id),
      asset_group_ids: assetGroups.map(o => o.id),
      team_ids: teams.map(o => o.id),
    }).finally(() => setSubmitting(false));
  };

  return (
    <Drawer open handleClose={onClose} title={title}>
      {injects === null
        ? <Spinner />
        : (
            <div style={{
              display: 'flex',
              flexDirection: 'column',
              gap: 16,
            }}
            >
              {previousVersion && (
                <Text variant="content-base">{t('Injects already in version {version} keep their targets.', { version: previousVersion })}</Text>
              )}
              {neededFields.has('assets') && (
                <TargetField label={t('Assets')} value={assets} onChange={setAssets} search={searchEndpointOptions} />
              )}
              {neededFields.has('asset_groups') && (
                <TargetField label={t('Asset groups')} value={assetGroups} onChange={setAssetGroups} search={searchAssetGroupOptions} />
              )}
              {neededFields.has('teams') && (
                <TargetField label={t('Teams')} value={teams} onChange={setTeams} search={searchTeamOptions} />
              )}
              <div style={{
                display: 'flex',
                flexDirection: 'column',
                gap: 8,
              }}
              >
                {injects.map((inject, index) => (
                  <div
                    key={inject.inject_catalog_id ?? index}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 8,
                    }}
                  >
                    <Text variant="content-base" style={{ flex: 1 }}>{inject.inject_title}</Text>
                    {inject.is_new && previousVersion && <Chip label={t('New')} />}
                    {inject.is_targeted || isTargeted(inject.target_fields)
                      ? <Chip label={t('Targeted')} severity="low" />
                      : <Chip label={t('Missing target')} severity="high" />}
                  </div>
                ))}
              </div>
              <div style={{
                display: 'flex',
                justifyContent: 'flex-end',
                gap: 8,
              }}
              >
                <Button priority="secondary" onClick={onClose}>{t('Cancel')}</Button>
                <Button onClick={submit} disabled={submitting || missing.length > 0}>{submitLabel}</Button>
              </div>
            </div>
          )}
    </Drawer>
  );
};

export default ScenarioTargetsDrawer;

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
import { fetchCatalogScenarioInstallPreview, installCatalogScenario } from '../../../../actions/catalog/scenario-catalog-actions';
import { searchTeams } from '../../../../actions/teams/team-actions';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import {
  type AssetGroupOutput,
  type CatalogInstallPreviewOutput,
  type CatalogItemOutput,
  type EndpointOutput,
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
  item: CatalogItemOutput;
  version: string;
  onClose: () => void;
  onInstalled: (scenarioId: string) => void;
}

// Targets are mandatory (F12): injects keep those of the installed version, the others get these.
const CatalogScenarioInstallDrawer: FunctionComponent<Props> = ({ item, version, onClose, onInstalled }) => {
  const { t } = useFormatter();
  const [preview, setPreview] = useState<CatalogInstallPreviewOutput | null>(null);
  const [assets, setAssets] = useState<Option[]>([]);
  const [assetGroups, setAssetGroups] = useState<Option[]>([]);
  const [teams, setTeams] = useState<Option[]>([]);
  const [installing, setInstalling] = useState(false);

  useEffect(() => {
    fetchCatalogScenarioInstallPreview(item.catalog_item_external_id, version).then(({ data }) => setPreview(data));
  }, [item.catalog_item_external_id, version]);

  const injects = preview?.injects ?? [];
  const neededFields = new Set(injects.filter(inject => !inject.is_targeted).flatMap(inject => inject.target_fields));
  const isTargeted = (fields: string[]) => (fields.includes('assets') && assets.length > 0)
    || (fields.includes('asset_groups') && assetGroups.length > 0)
    || (fields.includes('teams') && teams.length > 0);
  const missing = injects.filter(inject => !inject.is_targeted && !isTargeted(inject.target_fields));

  const submit = () => {
    setInstalling(true);
    installCatalogScenario(item.catalog_item_external_id, version, {
      asset_ids: assets.map(o => o.id),
      asset_group_ids: assetGroups.map(o => o.id),
      team_ids: teams.map(o => o.id),
    })
      .then(({ data }) => onInstalled(data.scenario_id))
      .finally(() => setInstalling(false));
  };

  return (
    <Drawer open handleClose={onClose} title={`${t('Install')} ${item.catalog_item_name ?? ''} v${version}`}>
      {preview === null
        ? <Spinner />
        : (
            <div style={{
              display: 'flex',
              flexDirection: 'column',
              gap: 16,
            }}
            >
              {preview.previous_version && (
                <Text variant="content-base">{t('Injects already in version {version} keep their targets.', { version: preview.previous_version })}</Text>
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
                {injects.map(inject => (
                  <div
                    key={inject.inject_catalog_id ?? inject.inject_title}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 8,
                    }}
                  >
                    <Text variant="content-base" style={{ flex: 1 }}>{inject.inject_title}</Text>
                    {inject.is_new && preview.previous_version && <Chip label={t('New')} />}
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
                <Button onClick={submit} disabled={installing || missing.length > 0}>{t('Install')}</Button>
              </div>
            </div>
          )}
    </Drawer>
  );
};

export default CatalogScenarioInstallDrawer;

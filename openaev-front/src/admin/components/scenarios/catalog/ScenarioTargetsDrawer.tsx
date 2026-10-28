import {
  Alert,
  Button,
  Chip,
  Combobox,
  ComboboxChips,
  ComboboxClear,
  ComboboxContent,
  ComboboxControls,
  ComboboxField,
  ComboboxInput,
  ComboboxTrigger,
  IconButton,
  Input,
  Spinner,
  Text,
} from '@filigran/design-system';
import { AccountTreeOutlined, DnsOutlined, EditOutlined, GroupsOutlined, PlayArrowOutlined } from '@mui/icons-material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, type ReactNode, useEffect, useState } from 'react';
import { Link } from 'react-router';

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
  type TargetLabel,
  type TeamOutput,
} from '../../../../utils/api-types';
import { type Option } from '../../../../utils/Option';

const ASSETS = 'assets';
const ASSET_GROUPS = 'asset_groups';
const TEAMS = 'teams';
const MANUAL = 'manual';
// Kinds an inject with a target selector falls back to, in this order, when it has no target.
const SELECTOR_KINDS = [ASSET_GROUPS, ASSETS, MANUAL];

// Every target of the tenant, not only those already used: first page of the search by name.
const searchAsOptions = <T, >(
  search: (input: SearchPaginationInput) => Promise<{ data: { content: T[] } }>,
  toOption: (item: T) => Option,
) => (text: string) => search({
  page: 0,
  size: 50,
  textSearch: text,
}).then(({ data }) => data.content.map(toOption));

const searchEndpointOptions = searchAsOptions<EndpointOutput>(searchEndpoints, endpoint => ({
  id: `${ASSETS}:${endpoint.asset_id}`,
  label: endpoint.asset_name,
}));
const searchAssetGroupOptions = searchAsOptions<AssetGroupOutput>(searchAssetGroups, group => ({
  id: `${ASSET_GROUPS}:${group.asset_group_id}`,
  label: group.asset_group_name,
}));
const searchTeamOptions = searchAsOptions<TeamOutput>(searchTeams, team => ({
  id: `${TEAMS}:${team.team_id}`,
  label: team.team_name,
}));
const searchEndpointAndGroupOptions = (text: string) => Promise.all([searchAssetGroupOptions(text), searchEndpointOptions(text)])
  .then(([groups, endpoints]) => [...groups, ...endpoints]);

const kindOf = (option: Option) => option.id.split(':')[0];
const idOf = (option: Option) => option.id.substring(option.id.indexOf(':') + 1);

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
    search(inputValue).then(setOptions);
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
      <ComboboxField aria-label={label}>
        <ComboboxChips />
        <ComboboxInput placeholder={t('Search...')} />
        <ComboboxControls>
          <ComboboxClear />
          <ComboboxTrigger />
        </ComboboxControls>
      </ComboboxField>
      <ComboboxContent emptyMessage={t('No available options')} />
    </Combobox>
  );
};

interface TargetRowProps {
  icon: ReactNode;
  title: string;
  subtitle: string;
  children: ReactNode;
}

const TargetRow: FunctionComponent<TargetRowProps> = ({ icon, title, subtitle, children }) => (
  <div style={{
    display: 'grid',
    gridTemplateColumns: '24px minmax(0, 1fr) minmax(0, 2fr)',
    gap: 12,
    alignItems: 'start',
  }}
  >
    {icon}
    <div>
      <Text variant="content-base">{title}</Text>
      <Text variant="content-caption">{subtitle}</Text>
    </div>
    {children}
  </div>
);

const injectIcon = (fields: string[]) => {
  if (fields.includes(MANUAL)) return <AccountTreeOutlined fontSize="small" />;
  if (fields.includes(ASSETS) || fields.includes(ASSET_GROUPS)) return <DnsOutlined fontSize="small" />;
  if (fields.includes(TEAMS)) return <GroupsOutlined fontSize="small" />;
  return <span />;
};

// A catalogue prerequisite the platform lacks; an injector comes with the integration to enable.
export interface MissingPrerequisite {
  requirement_kind: 'OPENAEV' | 'EDITION' | 'INJECTOR' | 'EXECUTOR' | 'CHAINING';
  requirement_key: string;
  requirement_label: string;
  requirement_catalog_connector_id?: string;
  requirement_catalog_connector_title?: string;
  requirement_status?: 'NOT_DEPLOYED' | 'NOT_RUNNING';
}

interface Props {
  title: string;
  mode: 'install' | 'launch';
  // The scenario whose injects can be opened from the list; none before install.
  scenarioId?: string;
  load: () => Promise<{
    injects: InjectPreview[];
    previousVersion?: string;
    missingPrerequisites?: MissingPrerequisite[];
  }>;
  onSubmit: (input: CatalogInstallInput) => Promise<unknown>;
  onClose: () => void;
}

// Section 6.3: the chosen targets are added to every inject that takes their kind, existing ones kept.
const ScenarioTargetsDrawer: FunctionComponent<Props> = ({ title, mode, scenarioId, load, onSubmit, onClose }) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const [injects, setInjects] = useState<InjectPreview[] | null>(null);
  const [previousVersion, setPreviousVersion] = useState<string | undefined>();
  const [missingPrerequisites, setMissingPrerequisites] = useState<MissingPrerequisite[]>([]);
  const [checking, setChecking] = useState(false);
  const [endpointsAndGroups, setEndpointsAndGroups] = useState<Option[]>([]);
  const [teams, setTeams] = useState<Option[]>([]);
  const [manual, setManual] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const loadPreview = () => load().then((loaded) => {
    setInjects(loaded.injects);
    setPreviousVersion(loaded.previousVersion);
    setMissingPrerequisites(loaded.missingPrerequisites ?? []);
  });
  useEffect(() => {
    loadPreview();
  }, []);
  const checkAgain = () => {
    setChecking(true);
    loadPreview().finally(() => setChecking(false));
  };

  const manualTargets = manual.split(/[\s,;]+/).map(target => target.trim()).filter(target => target.length > 0);
  const chosen: Record<string, TargetLabel[]> = {
    [ASSETS]: endpointsAndGroups.filter(o => kindOf(o) === ASSETS).map(o => ({
      kind: ASSETS,
      label: o.label,
    })),
    [ASSET_GROUPS]: endpointsAndGroups.filter(o => kindOf(o) === ASSET_GROUPS).map(o => ({
      kind: ASSET_GROUPS,
      label: o.label,
    })),
    [TEAMS]: teams.map(o => ({
      kind: TEAMS,
      label: o.label,
    })),
    [MANUAL]: manualTargets.map(target => ({
      kind: MANUAL,
      label: target,
    })),
  };

  // Same rule as the server: one kind at a time for an inject with a target selector.
  const addedTo = (inject: InjectPreview): TargetLabel[] => {
    const current = new Set((inject.current_targets ?? []).map(target => `${target.kind}:${target.label}`));
    const fresh = (kind: string) => chosen[kind].filter(target => !current.has(`${kind}:${target.label}`));
    if (inject.target_fields.includes(MANUAL)) {
      let kind = inject.target_selector ?? ASSET_GROUPS;
      if (chosen[kind].length === 0 && !inject.is_targeted) {
        kind = SELECTOR_KINDS.find(k => chosen[k].length > 0) ?? kind;
      }
      return fresh(kind);
    }
    return inject.target_fields.flatMap(fresh);
  };

  const statusChip = (targetable: boolean, targeted: boolean) => {
    if (!targetable) return <span />;
    return targeted
      ? <Chip label={t('Targeted')} severity="low" />
      : <Chip label={t('Missing target')} severity="high" />;
  };

  const all = injects ?? [];
  const takes = (kind: string) => all.some(inject => inject.target_fields.includes(kind));
  const missing = all.filter(inject => inject.target_fields.length > 0 && !inject.is_targeted && addedTo(inject).length === 0);
  const blocked = missing.length > 0 || missingPrerequisites.length > 0;

  const submit = () => {
    setSubmitting(true);
    onSubmit({
      asset_ids: endpointsAndGroups.filter(o => kindOf(o) === ASSETS).map(idOf),
      asset_group_ids: endpointsAndGroups.filter(o => kindOf(o) === ASSET_GROUPS).map(idOf),
      team_ids: teams.map(idOf),
      manual_targets: manualTargets,
    }).finally(() => setSubmitting(false));
  };

  const intro = mode === 'launch'
    ? t('Targets chosen here are added to every inject that takes this kind of target; existing targets are kept. They are saved on the scenario, then the simulation starts.')
    : t('Targets chosen here are added to every inject that takes this kind of target; existing targets are kept. They are saved on the installed scenario.');

  return (
    <Drawer open handleClose={onClose} title={title}>
      {injects === null
        ? <Spinner />
        : (
            <div style={{
              display: 'flex',
              flexDirection: 'column',
              gap: 24,
            }}
            >
              {missingPrerequisites.length > 0 && (
                <div style={{
                  display: 'flex',
                  flexDirection: 'column',
                  gap: 8,
                }}
                >
                  <Alert
                    severity="error"
                    title={t('This platform lacks what the scenario needs')}
                    description={t('Enable the missing integrations, then check again.')}
                  />
                  {missingPrerequisites.map(prerequisite => (
                    <div
                      key={`${prerequisite.requirement_kind}:${prerequisite.requirement_key}`}
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: 8,
                      }}
                    >
                      <Text variant="content-base" style={{ flex: 1 }}>
                        {prerequisite.requirement_catalog_connector_title ?? prerequisite.requirement_label}
                      </Text>
                      {prerequisite.requirement_status && (
                        <Chip
                          label={t(prerequisite.requirement_status === 'NOT_RUNNING' ? 'Not running' : 'Not deployed')}
                          severity="high"
                        />
                      )}
                      {prerequisite.requirement_kind === 'INJECTOR' && prerequisite.requirement_catalog_connector_id && (
                        <Button asChild size="sm" priority="secondary">
                          <Link to={`/admin/integrations/catalog/${prerequisite.requirement_catalog_connector_id}`} target="_blank">
                            {t('Enable integration')}
                          </Link>
                        </Button>
                      )}
                    </div>
                  ))}
                  <div>
                    <Button size="sm" priority="secondary" disabled={checking} onClick={checkAgain}>
                      {checking ? <Spinner size="sm" /> : t('Check again')}
                    </Button>
                  </div>
                </div>
              )}
              <Text variant="content-base">{intro}</Text>
              {previousVersion && (
                <Text variant="content-caption">{t('Injects already in version {version} keep their targets.', { version: previousVersion })}</Text>
              )}
              <div style={{
                display: 'flex',
                flexDirection: 'column',
                gap: 16,
              }}
              >
                {(takes(ASSETS) || takes(ASSET_GROUPS)) && (
                  <TargetRow icon={<DnsOutlined fontSize="small" />} title={t('Assets and asset groups')} subtitle={t('Endpoint injects')}>
                    <TargetField label={t('Assets and asset groups')} value={endpointsAndGroups} onChange={setEndpointsAndGroups} search={searchEndpointAndGroupOptions} />
                  </TargetRow>
                )}
                {takes(MANUAL) && (
                  <TargetRow icon={<AccountTreeOutlined fontSize="small" />} title={t('Manual targets')} subtitle={t('Injects in manual targeting')}>
                    <Input
                      aria-label={t('Manual targets')}
                      placeholder="e.g. 10.0.0.12, 10.20.0.0/24, app.example.com"
                      value={manual}
                      onChange={event => setManual(event.target.value)}
                    />
                  </TargetRow>
                )}
                {takes(TEAMS) && (
                  <TargetRow icon={<GroupsOutlined fontSize="small" />} title={t('Teams and players')} subtitle={t('Human injects: mail, SMS, challenges')}>
                    <TargetField label={t('Teams and players')} value={teams} onChange={setTeams} search={searchTeamOptions} />
                  </TargetRow>
                )}
              </div>
              <div>
                <Text variant="content-base-bold">{`${t('Injects')} (${all.length})`}</Text>
                <Text variant="content-caption">{t('Targets marked "added" come from this choice; the others were already on the scenario.')}</Text>
              </div>
              <div style={{
                display: 'flex',
                flexDirection: 'column',
              }}
              >
                {all.map((inject, index) => {
                  const added = addedTo(inject);
                  const targeted = inject.is_targeted || added.length > 0;
                  return (
                    <div
                      key={inject.inject_catalog_id ?? index}
                      style={{
                        display: 'grid',
                        gridTemplateColumns: '24px minmax(0, 2fr) minmax(0, 2fr) auto auto',
                        gap: 12,
                        alignItems: 'center',
                        padding: '8px 0',
                        borderBottom: `1px solid ${theme.palette.divider}`,
                      }}
                    >
                      {injectIcon(inject.target_fields)}
                      <div style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: 8,
                      }}
                      >
                        <Text variant="content-base">{inject.inject_title}</Text>
                        {inject.is_new && previousVersion && <Chip label={t('New')} />}
                      </div>
                      <div style={{
                        display: 'flex',
                        gap: 4,
                        flexWrap: 'wrap',
                      }}
                      >
                        {(inject.current_targets ?? []).map(target => <Chip key={`${target.kind}:${target.label}`} label={target.label} />)}
                        {added.map(target => <Chip key={`added:${target.kind}:${target.label}`} label={`${target.label} · ${t('added')}`} severity="info" />)}
                      </div>
                      {statusChip(inject.target_fields.length > 0, targeted)}
                      {mode === 'launch' && scenarioId
                        ? (
                            <IconButton asChild size="sm" priority="tertiary" aria-label={t('Edit the injects')} icon={<EditOutlined fontSize="small" />}>
                              <Link to={`/admin/scenarios/${scenarioId}/injects`} />
                            </IconButton>
                          )
                        : <span />}
                    </div>
                  );
                })}
              </div>
              {missing.length > 0 && (
                <Alert
                  severity="warning"
                  title={missing.length === 1 ? t('1 inject has no target') : t('{count} injects have no target', { count: String(missing.length) })}
                  description={mode === 'launch'
                    ? t('Launch stays disabled until each targetable inject has a target.')
                    : t('Install stays disabled until each targetable inject has a target.')}
                />
              )}
              <div style={{
                display: 'flex',
                justifyContent: 'flex-end',
                gap: 8,
              }}
              >
                <Button priority="secondary" onClick={onClose}>{t('Cancel')}</Button>
                <Button
                  onClick={submit}
                  disabled={submitting || blocked}
                  startIcon={mode === 'launch' ? <PlayArrowOutlined fontSize="small" /> : undefined}
                >
                  {mode === 'launch' ? t('Save targets and launch') : t('Install')}
                </Button>
              </div>
            </div>
          )}
    </Drawer>
  );
};

export default ScenarioTargetsDrawer;

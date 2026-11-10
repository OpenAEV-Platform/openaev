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
  Drawer,
  DrawerBody,
  DrawerContent,
  DrawerFooter,
  DrawerHeader,
  IconButton,
  Input,
  Spinner,
  Text,
} from '@filigran/design-system';
import { AccountTreeOutlined, DnsOutlined, EditOutlined, GroupsOutlined, PlayArrowOutlined } from '@mui/icons-material';
import { type FunctionComponent, type ReactNode, useEffect, useState } from 'react';
import { Link } from 'react-router';

import { searchAssetGroups } from '../../../../actions/asset_groups/assetgroup-action';
import { searchEndpoints } from '../../../../actions/assets/endpoint-actions';
import { searchTeams } from '../../../../actions/teams/team-actions';
import { useFormatter } from '../../../../components/i18n';
import {
  type AssetGroupOutput,
  type CatalogInstallInput,
  type EndpointOutput,
  type InjectPreview,
  type SearchPaginationInput,
  type TargetLabel,
  type TeamOutput,
  type WorkflowConfigurationInput,
} from '../../../../utils/api-types';
import { type Option } from '../../../../utils/Option';
import { EMPTY_SCOPE, type ScopeSelection, scopeSelectionCount, selectionToRules } from '../../autonomous/useAutonomousRunConfig';
import ChainedScopeFields from './ChainedScopeFields';

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

// A group of rows on one surface, rows split by a line: the targets form and the injects list.
const GROUP_STYLE = {
  display: 'flex',
  flexDirection: 'column',
  backgroundColor: 'var(--bg-elevation-default)',
  border: '1px solid var(--border-elevation-subtle-soft)',
  borderRadius: 4,
  overflow: 'hidden',
} as const;
const ROW_DIVIDER = '1px solid var(--border-elevation-subtle-soft)';

// Label and field on the same grid in every row, so the inputs line up.
const TargetRow: FunctionComponent<TargetRowProps> = ({ icon, title, subtitle, children }) => (
  <div style={{
    display: 'grid',
    gridTemplateColumns: '20px minmax(0, 1fr) minmax(0, 1.4fr)',
    gap: 12,
    alignItems: 'center',
    padding: '12px 16px',
  }}
  >
    {icon}
    <div>
      <Text variant="content-base-bold">{title}</Text>
      <Text variant="content-caption" className="text-default-secondary">{subtitle}</Text>
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
  requirement_kind: 'OPENAEV' | 'EDITION' | 'INJECTOR' | 'EXECUTOR';
  requirement_key: string;
  requirement_label: string;
  requirement_catalog_connector_id?: string;
  requirement_catalog_connector_title?: string;
  requirement_status?: 'NOT_DEPLOYED' | 'NOT_RUNNING';
}

// What the install itself cannot do without; the rest only stops the run (as the server rules).
const blocksInstall = (kind: MissingPrerequisite['requirement_kind']) => kind === 'OPENAEV' || kind === 'INJECTOR';

interface Props {
  // Header: what the drawer does (overline), the scenario (title) and its version (badge).
  overline: string;
  title: string;
  version?: string;
  mode: 'install' | 'launch';
  // Install, then launch the installed scenario.
  thenLaunch?: boolean;
  // A chained scenario takes its scope instead of targets.
  chained?: boolean;
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
const ScenarioTargetsDrawer: FunctionComponent<Props> = ({ overline, title, version, mode, thenLaunch = false, chained = false, scenarioId, load, onSubmit, onClose }) => {
  const { t } = useFormatter();
  const [injects, setInjects] = useState<InjectPreview[] | null>(null);
  const [previousVersion, setPreviousVersion] = useState<string | undefined>();
  const [missingPrerequisites, setMissingPrerequisites] = useState<MissingPrerequisite[]>([]);
  const [checking, setChecking] = useState(false);
  const [endpointsAndGroups, setEndpointsAndGroups] = useState<Option[]>([]);
  const [teams, setTeams] = useState<Option[]>([]);
  const [manual, setManual] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [allowScope, setAllowScope] = useState<ScopeSelection>(EMPTY_SCOPE);
  const [denyScope, setDenyScope] = useState<ScopeSelection>(EMPTY_SCOPE);
  const [limits, setLimits] = useState<WorkflowConfigurationInput>({
    workflow_configuration_timeout_enabled: false,
    workflow_configuration_timeout_seconds: 3600,
    workflow_configuration_rate_limit_enabled: false,
    workflow_configuration_safe_mode_enabled: true,
  });
  const chainedInstall = chained && mode === 'install';

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
      // DS "high" uses the warning tokens: the same tone as the warning under the list.
      : <Chip label={t('Missing target')} severity="high" />;
  };

  const all = injects ?? [];
  const takes = (kind: string) => all.some(inject => inject.target_fields.includes(kind));
  const missing = all.filter(inject => inject.target_fields.length > 0 && !inject.is_targeted && addedTo(inject).length === 0);
  const blockingPrerequisites = mode === 'install'
    ? missingPrerequisites.filter(prerequisite => blocksInstall(prerequisite.requirement_kind))
    : missingPrerequisites;
  // Installing goes on without targets or a run-only requirement: the scenario health check reports them.
  const blocked = blockingPrerequisites.length > 0
    || (mode === 'launch' && missing.length > 0)
    || (chainedInstall && scopeSelectionCount(allowScope) === 0);

  const submit = () => {
    setSubmitting(true);
    onSubmit({
      asset_ids: endpointsAndGroups.filter(o => kindOf(o) === ASSETS).map(idOf),
      asset_group_ids: endpointsAndGroups.filter(o => kindOf(o) === ASSET_GROUPS).map(idOf),
      team_ids: teams.map(idOf),
      manual_targets: manualTargets,
      workflow_configuration: chainedInstall
        ? {
            ...limits,
            workflow_scope_rules: [...selectionToRules(allowScope, 'ALLOWLIST'), ...selectionToRules(denyScope, 'DENYLIST')],
          }
        : undefined,
    }).finally(() => setSubmitting(false));
  };

  const intro = mode === 'launch'
    ? t('Targets chosen here are added to every inject that takes this kind of target; existing targets are kept. They are saved on the scenario, then the simulation starts.')
    : t('Targets chosen here are added to every inject that takes this kind of target; existing targets are kept. They are saved on the installed scenario.');

  const prerequisiteName = (prerequisite: MissingPrerequisite) => prerequisite.requirement_catalog_connector_title ?? prerequisite.requirement_label;
  const checkAgainButton = (
    <Button size="sm" priority="tertiary" disabled={checking} onClick={checkAgain}>
      {checking ? <Spinner size="sm" /> : t('Check again')}
    </Button>
  );

  return (
    <Drawer open onOpenChange={next => !next && onClose()}>
      <DrawerContent size="lg" busy={submitting}>
        <DrawerHeader
          overline={overline}
          title={title}
          badge={version ? <Chip label={`v${version}`} /> : undefined}
          closeLabel={t('Close')}
        />
        <DrawerBody>
          {injects === null
            ? <Spinner />
            : (
                // FDS-WORKAROUND: Alert title/description are <p> with browser margins — remove when the library resets them — see DS Alert
                <div
                  className="targets-drawer-content"
                  style={{
                    display: 'flex',
                    flexDirection: 'column',
                    gap: 24,
                  }}
                >
                  <style>{'.targets-drawer-content p { margin: 0; }'}</style>
                  {/* What blocks first, self-contained: each missing integration, its status and its fixes in the alert. */}
                  {missingPrerequisites.length > 0 && (
                    <Alert
                      elevation={1}
                      severity={blockingPrerequisites.length > 0 ? 'error' : 'warning'}
                      title={t('This platform lacks what the scenario needs')}
                      // The Alert renders its description in a <p>: rows are spans (phrasing content), not divs.
                      description={(
                        <span style={{
                          display: 'flex',
                          flexDirection: 'column',
                          gap: 12,
                        }}
                        >
                          <span>
                            {blockingPrerequisites.length > 0
                              ? t('Enable the missing integrations, then check again.')
                              : t('You can install it now; it cannot run until these are available.')}
                          </span>
                          {missingPrerequisites.map((prerequisite, index) => (
                            <span
                              key={`${prerequisite.requirement_kind}:${prerequisite.requirement_key}`}
                              style={{
                                display: 'flex',
                                alignItems: 'center',
                                gap: 8,
                                paddingTop: 12,
                                borderTop: `1px solid var(${blockingPrerequisites.length > 0 ? '--border-alert-error' : '--border-alert-warning'})`,
                              }}
                            >
                              <Text as="span" variant="content-base-bold">{prerequisiteName(prerequisite)}</Text>
                              {prerequisite.requirement_status && (
                                <Chip
                                  label={t(prerequisite.requirement_status === 'NOT_RUNNING' ? 'Not running' : 'Not deployed')}
                                  severity="critical"
                                />
                              )}
                              <span style={{ flex: 1 }} />
                              {/* One Check again: beside the fix of the last row. */}
                              {index === missingPrerequisites.length - 1 && checkAgainButton}
                              {prerequisite.requirement_kind === 'INJECTOR' && prerequisite.requirement_catalog_connector_id && (
                                <Button asChild size="sm">
                                  <Link to={`/admin/integrations/catalog/${prerequisite.requirement_catalog_connector_id}`} target="_blank">
                                    {t('Enable integration')}
                                  </Link>
                                </Button>
                              )}
                            </span>
                          ))}
                        </span>
                      )}
                    />
                  )}
                  {chainedInstall
                    ? (
                        <>
                          <ChainedScopeFields
                            allow={allowScope}
                            deny={denyScope}
                            limits={limits}
                            onAllowChange={setAllowScope}
                            onDenyChange={setDenyScope}
                            onLimitsChange={setLimits}
                          />
                          {scopeSelectionCount(allowScope) === 0 && (
                            <Alert elevation={1} severity="warning" title={t('Add at least one entry to the allow list.')} />
                          )}
                        </>
                      )
                    : (
                        <>
                          {/* Targets read as a form: the explanation is its helper text, the fields share one grid. */}
                          <div style={{
                            display: 'flex',
                            flexDirection: 'column',
                            gap: 8,
                          }}
                          >
                            <Text variant="content-caption" className="text-default-secondary">{intro}</Text>
                            {previousVersion && (
                              <Text variant="content-caption" className="text-default-secondary">
                                {t('Injects already in version {version} keep their targets.', { version: previousVersion })}
                              </Text>
                            )}
                            {(takes(ASSETS) || takes(ASSET_GROUPS) || takes(MANUAL) || takes(TEAMS)) && (
                              <div style={GROUP_STYLE}>
                                {[
                                  (takes(ASSETS) || takes(ASSET_GROUPS)) && (
                                    <TargetRow key="assets" icon={<DnsOutlined fontSize="small" />} title={t('Assets and asset groups')} subtitle={t('Endpoint injects')}>
                                      <TargetField label={t('Assets and asset groups')} value={endpointsAndGroups} onChange={setEndpointsAndGroups} search={searchEndpointAndGroupOptions} />
                                    </TargetRow>
                                  ),
                                  takes(MANUAL) && (
                                    <TargetRow key="manual" icon={<AccountTreeOutlined fontSize="small" />} title={t('Manual targets')} subtitle={t('Injects in manual targeting')}>
                                      <Input
                                        aria-label={t('Manual targets')}
                                        placeholder="e.g. 10.0.0.12, 10.20.0.0/24, app.example.com"
                                        value={manual}
                                        onChange={event => setManual(event.target.value)}
                                      />
                                    </TargetRow>
                                  ),
                                  takes(TEAMS) && (
                                    <TargetRow key="teams" icon={<GroupsOutlined fontSize="small" />} title={t('Teams and players')} subtitle={t('Human injects: mail, SMS, challenges')}>
                                      <TargetField label={t('Teams and players')} value={teams} onChange={setTeams} search={searchTeamOptions} />
                                    </TargetRow>
                                  ),
                                ].filter(Boolean).map((row, index) => (
                                  <div key={index} style={{ borderTop: index > 0 ? ROW_DIVIDER : undefined }}>{row}</div>
                                ))}
                              </div>
                            )}
                          </div>
                          <div style={{
                            display: 'flex',
                            flexDirection: 'column',
                            gap: 8,
                          }}
                          >
                            <div>
                              <Text variant="content-base-bold">{`${t('Injects')} (${all.length})`}</Text>
                              <Text variant="content-caption" className="text-default-secondary">{t('Targets marked "added" come from this choice; the others were already on the scenario.')}</Text>
                            </div>
                            {/* The list, then the warning about it, on one surface. */}
                            <div style={GROUP_STYLE}>
                              {all.map((inject, index) => {
                                const added = addedTo(inject);
                                const targeted = inject.is_targeted || added.length > 0;
                                return (
                                  <div
                                    key={inject.inject_catalog_id ?? index}
                                    style={{
                                      display: 'grid',
                                      gridTemplateColumns: '20px minmax(0, 2fr) minmax(0, 1fr) auto auto',
                                      gap: 12,
                                      alignItems: 'center',
                                      padding: '12px 16px',
                                      borderTop: index > 0 ? ROW_DIVIDER : undefined,
                                    }}
                                  >
                                    {injectIcon(inject.target_fields)}
                                    <div style={{
                                      display: 'flex',
                                      alignItems: 'center',
                                      gap: 8,
                                      minWidth: 0,
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
                              {missing.length > 0 && (
                                <div style={{ borderTop: all.length > 0 ? ROW_DIVIDER : undefined }}>
                                  <Alert
                                    elevation={1}
                                    severity="warning"
                                    title={missing.length === 1 ? t('1 inject has no target') : t('{count} injects have no target', { count: String(missing.length) })}
                                    description={mode === 'launch'
                                      ? t('Launch stays disabled until each targetable inject has a target.')
                                      : t('You can install it now and set the targets later from the scenario.')}
                                  />
                                </div>
                              )}
                            </div>
                          </div>
                        </>
                      )}
                </div>
              )}
          {injects !== null && (
            <DrawerFooter>
              <Button priority="secondary" disabled={submitting} onClick={onClose}>{t('Cancel')}</Button>
              <Button
                onClick={submit}
                disabled={submitting || blocked}
                startIcon={mode === 'launch' || thenLaunch ? <PlayArrowOutlined fontSize="small" /> : undefined}
              >
                {mode === 'launch' ? t('Save targets and launch') : t(thenLaunch ? 'Install and launch' : 'Install')}
              </Button>
            </DrawerFooter>
          )}
        </DrawerBody>
      </DrawerContent>
    </Drawer>
  );
};

export default ScenarioTargetsDrawer;

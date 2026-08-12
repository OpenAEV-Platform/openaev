import {
  BlockOutlined,
  DnsOutlined,
  EditOutlined,
  GroupsOutlined,
  InfoOutlined,
  PersonOutlined,
  PublicOutlined,
  TaskAltOutlined,
} from '@mui/icons-material';
import { Box, Button, Chip, Paper, Tooltip, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { SelectGroup } from 'mdi-material-ui';
import { type ReactElement, useState } from 'react';

import type { AssetGroupsHelper } from '../../../actions/asset_groups/assetgroup-helper';
import type { EndpointHelper } from '../../../actions/assets/asset-helper';
import type { UserHelper } from '../../../actions/helper';
import type { TeamsHelper } from '../../../actions/teams/team-helper';
import Drawer from '../../../components/common/Drawer';
import { useFormatter } from '../../../components/i18n';
import PlatformIcon, { hasPlatformIcon } from '../../../components/PlatformIcon';
import { useHelper } from '../../../store';
import type {
  WorkflowConfigurationInput,
  WorkflowConfigurationOutput,
  WorkflowScopeRuleInput,
  WorkflowScopeRuleOutput,
} from '../../../utils/api-types';
import { type AssetCategory } from '../assets/asset-categories';
import AssetCategoryIcon from '../assets/AssetCategoryIcon';
import ScopeForm, { type ScopeCustomRule } from './ScopeForm';

// Asset categories whose OS platform brand icon (Windows / Linux / macOS) is the meaningful glyph;
// every other category (web app, cloud, network device, identity, ...) has no OS platform and is
// represented by its taxonomy glyph instead - mirrors atomic testing's TargetIcon.
const OS_PLATFORM_CATEGORIES = new Set<AssetCategory>(['HOST', 'MOBILE_DEVICE']);

type ScopeMode = 'ALLOWLIST' | 'DENYLIST';

interface ScopeRulesProps {
  workflowId: string;
  workflowConfiguration: WorkflowConfigurationOutput | undefined;
  onUpdate: (overrides: Partial<WorkflowConfigurationInput>) => void;
  readOnly?: boolean;
}

// Visual grouping of scope entries by kind, so a scope with many entries reads as a scannable set of
// typed sections (Assets / Asset groups / Teams / Persons / IPs & hostnames) instead of one long
// comma-separated string. Each group carries the same typed icon used in the picker.
interface ScopeGroupMeta {
  key: string;
  label: string;
  icon: ReactElement;
  matches: (source: WorkflowScopeRuleOutput['workflow_scope_rule_source']) => boolean;
}

const SCOPE_GROUPS: ScopeGroupMeta[] = [
  {
    key: 'ASSET',
    label: 'Assets',
    icon: <DnsOutlined fontSize="small" />,
    matches: source => source === 'ASSET',
  },
  {
    key: 'ASSET_GROUP',
    label: 'Asset groups',
    icon: <SelectGroup fontSize="small" />,
    matches: source => source === 'ASSET_GROUP',
  },
  {
    key: 'TEAM',
    label: 'Teams',
    icon: <GroupsOutlined fontSize="small" />,
    matches: source => source === 'TEAM',
  },
  {
    key: 'PLAYER',
    label: 'Persons',
    icon: <PersonOutlined fontSize="small" />,
    matches: source => source === 'PLAYER',
  },
  {
    key: 'MANUAL',
    label: 'IPs & hostnames',
    icon: <PublicOutlined fontSize="small" />,
    matches: source => source === 'MANUAL' || source === 'CSV',
  },
];

interface ScopeColumnProps {
  title: string;
  rules: WorkflowScopeRuleOutput[];
  resolveLabel: (rule: WorkflowScopeRuleOutput) => string;
  /** Per-entry glyph (asset platform / category, team, person, ...) so a chip reads like it does
     *  everywhere else in the app rather than a bare label. */
  resolveIcon: (rule: WorkflowScopeRuleOutput) => ReactElement;
  onAdd: () => void;
  readOnly?: boolean;
  /** Semantic accent - green for the allow-list, red for the deny-list - for instant scanning. */
  accent: string;
  headerIcon: ReactElement;
  /** Optional info hint shown next to the title (e.g. deny-over-allow precedence). */
  infoTooltip?: string;
}

// Each list (allow / deny) is a self-contained card: a colored top strip for instant semantic
// scanning, a header with its typed icon + count + Add affordance, and a grouped, chip-based body.
const ScopeColumn = ({
  title,
  rules,
  resolveLabel,
  resolveIcon,
  onAdd,
  readOnly = false, accent,
  headerIcon,
  infoTooltip,
}: ScopeColumnProps) => {
  // Standard hooks
  const { t } = useFormatter();
  const theme = useTheme();

  const groups = SCOPE_GROUPS.map(group => ({
    meta: group,
    rules: rules.filter(rule => group.matches(rule.workflow_scope_rule_source)),
  })).filter(group => group.rules.length > 0);

  return (
    <Paper
      variant="outlined"
      sx={{
        height: '100%',
        display: 'grid',
        gridTemplateRows: 'min-content 1fr',
        gap: 1.5,
        minHeight: 168,
        overflow: 'hidden',
        borderTop: `3px solid ${alpha(accent, 0.8)}`,
        p: theme.spacing(2),
      }}
    >
      <Box
        sx={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: theme.spacing(2),
        }}
      >
        <Box sx={{
          display: 'flex',
          alignItems: 'center',
          gap: theme.spacing(1),
          color: accent,
        }}
        >
          {headerIcon}
          <Typography variant="subtitle2" sx={{ color: 'text.primary' }}>
            {title}
          </Typography>
          <Chip
            label={rules.length}
            size="small"
            sx={{
              height: 20,
              minWidth: 24,
              fontWeight: 700,
              color: accent,
              backgroundColor: alpha(accent, 0.12),
            }}
          />
          {infoTooltip && (
            <Tooltip title={infoTooltip}>
              <InfoOutlined
                color="primary"
                sx={{
                  fontSize: 16,
                  cursor: 'pointer',
                }}
              />
            </Tooltip>
          )}
        </Box>

        <Button size="small" startIcon={<EditOutlined />} onClick={onAdd} disabled={readOnly}>
          {t('Define')}
        </Button>
      </Box>

      {groups.length > 0 ? (
        <Box sx={{
          display: 'grid',
          gap: theme.spacing(1.5),
          alignContent: 'start',
        }}
        >
          {groups.map(({ meta, rules: groupRules }) => (
            <Box key={meta.key}>
              <Box sx={{
                display: 'flex',
                alignItems: 'center',
                gap: theme.spacing(0.75),
                color: 'text.secondary',
                marginBottom: theme.spacing(0.75),
              }}
              >
                {meta.icon}
                <Typography variant="caption" sx={{ fontWeight: 600 }}>
                  {`${t(meta.label)} (${groupRules.length})`}
                </Typography>
              </Box>
              <Box sx={{
                display: 'flex',
                flexWrap: 'wrap',
                gap: theme.spacing(0.75),
              }}
              >
                {groupRules.map((rule) => {
                  const label = resolveLabel(rule);
                  return (
                    <Chip
                      key={rule.workflow_scope_rule_id ?? `${rule.workflow_scope_rule_source}-${rule.workflow_scope_rule_value}`}
                      icon={(
                        <Box
                          component="span"
                          sx={{
                            display: 'inline-flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            color: 'text.secondary',
                          }}
                        >
                          {resolveIcon(rule)}
                        </Box>
                      )}
                      label={rule.workflow_scope_rule_snapshot_start_label ?? label}
                      size="small"
                      variant="outlined"
                      sx={{
                        'maxWidth': '100%',
                        'borderColor': alpha(accent, 0.35),
                        '& .MuiChip-icon': { marginLeft: theme.spacing(0.75) },
                      }}
                    />
                  );
                })}
              </Box>
            </Box>
          ))}
        </Box>
      ) : (
        <Box sx={{
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          gap: theme.spacing(1),
          border: `1px dashed ${theme.palette.divider}`,
          borderRadius: 1,
          padding: theme.spacing(2),
          color: 'text.disabled',
        }}
        >
          <Typography variant="body2" sx={{ color: 'text.disabled' }}>
            {t('Nothing added yet.')}
          </Typography>
          <Button size="small" startIcon={<EditOutlined />} onClick={onAdd} disabled={readOnly}>
            {t('Define')}
          </Button>
        </Box>
      )}
    </Paper>
  );
};

const ScopeRules = ({ workflowId, workflowConfiguration, onUpdate, readOnly = false }: ScopeRulesProps) => {
  const { t } = useFormatter();
  const theme = useTheme();

  // Drawer state
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [drawerMode, setDrawerMode] = useState<ScopeMode>('ALLOWLIST');

  // Selected IDs for the form
  const [selectedEndpointIds, setSelectedEndpointIds] = useState<string[]>([]);
  const [selectedAssetGroupIds, setSelectedAssetGroupIds] = useState<string[]>([]);
  const [selectedTeamIds, setSelectedTeamIds] = useState<string[]>([]);
  const [selectedPlayerIds, setSelectedPlayerIds] = useState<string[]>([]);
  const [selectedCustomRules, setSelectedCustomRules] = useState<ScopeCustomRule[]>([]);
  const [initialEndpointIds, setInitialEndpointIds] = useState<string[]>([]);
  const [initialAssetGroupIds, setInitialAssetGroupIds] = useState<string[]>([]);
  const [initialTeamIds, setInitialTeamIds] = useState<string[]>([]);
  const [initialPlayerIds, setInitialPlayerIds] = useState<string[]>([]);
  const [initialCustomRules, setInitialCustomRules] = useState<ScopeCustomRule[]>([]);

  const handleOpenDrawer = (mode: ScopeMode) => {
    if (readOnly) return;
    setDrawerMode(mode);

    // Pre-populate with existing rules for the given mode
    const scopeRules = workflowConfiguration?.workflow_scope_rules ?? [];
    const rulesForMode = scopeRules.filter(r => r.workflow_scope_rule_selected_mode === mode);
    const endpointIds = rulesForMode
      .filter(r => r.workflow_scope_rule_source === 'ASSET')
      .map(r => r.workflow_scope_rule_value ?? '')
      .filter(Boolean);
    const assetGroupIds = rulesForMode
      .filter(r => r.workflow_scope_rule_source === 'ASSET_GROUP')
      .map(r => r.workflow_scope_rule_value ?? '')
      .filter(Boolean);
    const teamIds = rulesForMode
      .filter(r => r.workflow_scope_rule_source === 'TEAM')
      .map(r => r.workflow_scope_rule_value ?? '')
      .filter(Boolean);
    const playerIds = rulesForMode
      .filter(r => r.workflow_scope_rule_source === 'PLAYER')
      .map(r => r.workflow_scope_rule_value ?? '')
      .filter(Boolean);
    const customRules = rulesForMode
      .filter(r => r.workflow_scope_rule_source === 'MANUAL' || r.workflow_scope_rule_source === 'CSV')
      .map(r => ({
        source: (r.workflow_scope_rule_source as 'MANUAL' | 'CSV') ?? 'CSV',
        value: r.workflow_scope_rule_value ?? '',
      }))
      .filter(r => !!r.value);

    setSelectedEndpointIds(endpointIds);
    setSelectedAssetGroupIds(assetGroupIds);
    setSelectedTeamIds(teamIds);
    setSelectedPlayerIds(playerIds);
    setSelectedCustomRules(customRules);
    setInitialEndpointIds(endpointIds);
    setInitialAssetGroupIds(assetGroupIds);
    setInitialTeamIds(teamIds);
    setInitialPlayerIds(playerIds);
    setInitialCustomRules(customRules);

    setDrawerOpen(true);
  };

  const handleCloseDrawer = () => setDrawerOpen(false);

  const handleSubmitScope = () => {
    // Build a lookup of existing rule IDs by source+value so we can preserve them
    const existingRulesForMode = (workflowConfiguration?.workflow_scope_rules ?? [])
      .filter(r => r.workflow_scope_rule_selected_mode === drawerMode);
    const existingIdMap = new Map<string, string>();
    for (const r of existingRulesForMode) {
      if (r.workflow_scope_rule_id && r.workflow_scope_rule_source && r.workflow_scope_rule_value) {
        existingIdMap.set(`${r.workflow_scope_rule_source}:${r.workflow_scope_rule_value}`, r.workflow_scope_rule_id);
      }
    }

    // Build new rules for the current drawer mode, preserving IDs for existing rules
    const newRulesForMode: WorkflowScopeRuleInput[] = [
      ...selectedEndpointIds.map(id => ({
        workflow_scope_rule_id: existingIdMap.get(`ASSET:${id}`),
        workflow_scope_rule_selected_mode: drawerMode,
        workflow_scope_rule_source: 'ASSET' as const,
        workflow_scope_rule_value: id,
      })),
      ...selectedAssetGroupIds.map(id => ({
        workflow_scope_rule_id: existingIdMap.get(`ASSET_GROUP:${id}`),
        workflow_scope_rule_selected_mode: drawerMode,
        workflow_scope_rule_source: 'ASSET_GROUP' as const,
        workflow_scope_rule_value: id,
      })),
      ...selectedTeamIds.map(id => ({
        workflow_scope_rule_id: existingIdMap.get(`TEAM:${id}`),
        workflow_scope_rule_selected_mode: drawerMode,
        workflow_scope_rule_source: 'TEAM' as const,
        workflow_scope_rule_value: id,
      })),
      ...selectedPlayerIds.map(id => ({
        workflow_scope_rule_id: existingIdMap.get(`PLAYER:${id}`),
        workflow_scope_rule_selected_mode: drawerMode,
        workflow_scope_rule_source: 'PLAYER' as const,
        workflow_scope_rule_value: id,
      })),
      ...selectedCustomRules.map(rule => ({
        workflow_scope_rule_id: existingIdMap.get(`${rule.source}:${rule.value}`),
        workflow_scope_rule_selected_mode: drawerMode,
        workflow_scope_rule_source: rule.source,
        workflow_scope_rule_value: rule.value,
      })),
    ];

    // Keep existing rules for the OTHER mode, replace rules for the current mode
    const otherMode: ScopeMode = drawerMode === 'ALLOWLIST' ? 'DENYLIST' : 'ALLOWLIST';
    const existingRulesOtherMode: WorkflowScopeRuleInput[] = (workflowConfiguration?.workflow_scope_rules ?? [])
      .filter(r => r.workflow_scope_rule_selected_mode === otherMode)
      .map(r => ({
        workflow_scope_rule_id: r.workflow_scope_rule_id,
        workflow_scope_rule_selected_mode: r.workflow_scope_rule_selected_mode!,
        workflow_scope_rule_source: r.workflow_scope_rule_source!,
        workflow_scope_rule_value: r.workflow_scope_rule_value!,
      }));

    onUpdate({ workflow_scope_rules: [...existingRulesOtherMode, ...newRulesForMode] });

    handleCloseDrawer();
  };

  const drawerTitle = drawerMode === 'ALLOWLIST'
    ? t('Define allowlisted scope')
    : t('Define denylisted scope');

  const { endpointsMap, assetGroupsMap, teamsMap, usersMap } = useHelper(
    (helper: EndpointHelper & AssetGroupsHelper & TeamsHelper & UserHelper) => ({
      endpointsMap: helper.getEndpointsMap(),
      assetGroupsMap: helper.getAssetGroupMaps(),
      teamsMap: helper.getTeamsMap(),
      usersMap: helper.getUsersMap(),
    }),
  );

  // Live-first, snapshot-fallback resolution. The live inventory lookup keeps the label in sync with
  // renames; when the referenced asset / group has been deleted the backend-persisted snapshot
  // (workflow_scope_rule_value_label) keeps a past simulation's scope readable. Only when neither is
  // available (a pre-migration rule whose asset was already gone) do we show a generic "Deleted"
  // message rather than the raw id or a permanent "Loading...".
  const resolveLabel = (rule: WorkflowScopeRuleOutput): string => {
    const value = rule.workflow_scope_rule_value ?? '';
    const snapshotLabel = rule.workflow_scope_rule_value_label ?? undefined;
    const unresolvedLabel = t('Loading...');

    switch (rule.workflow_scope_rule_source) {
      case 'ASSET': {
        const endpoint = endpointsMap[value];
        return endpoint?.asset_name ?? snapshotLabel ?? t('Deleted asset');
      }
      case 'ASSET_GROUP': {
        const group = assetGroupsMap[value];
        return group?.asset_group_name ?? snapshotLabel ?? t('Deleted asset group');
      }
      case 'TEAM': {
        const team = teamsMap[value];
        return team?.team_name ?? unresolvedLabel;
      }
      case 'PLAYER': {
        const user = usersMap[value];
        if (!user) return unresolvedLabel;
        const name = `${user.user_firstname ?? ''} ${user.user_lastname ?? ''}`.trim();
        return name.length > 0 ? name : (user.user_email ?? unresolvedLabel);
      }
      default:
        return value || unresolvedLabel;
    }
  };

  const resolveIcon = (rule: WorkflowScopeRuleOutput): ReactElement => {
    const value = rule.workflow_scope_rule_value ?? '';
    switch (rule.workflow_scope_rule_source) {
      case 'ASSET': {
        const endpoint = endpointsMap[value];
        const platform = endpoint?.endpoint_platform;
        const category = endpoint?.asset_category as AssetCategory | undefined;
        // Host-like assets keep their OS brand icon; everything else uses its taxonomy glyph.
        if (hasPlatformIcon(platform) && (!category || OS_PLATFORM_CATEGORIES.has(category))) {
          return <PlatformIcon platform={platform as string} width={16} />;
        }
        return <AssetCategoryIcon category={category ?? null} sx={{ fontSize: 16 }} />;
      }
      case 'ASSET_GROUP':
        return <SelectGroup sx={{ fontSize: 16 }} />;
      case 'TEAM':
        return <GroupsOutlined sx={{ fontSize: 16 }} />;
      case 'PLAYER':
        return <PersonOutlined sx={{ fontSize: 16 }} />;
      default:
        return <PublicOutlined sx={{ fontSize: 16 }} />;
    }
  };

  const scopeRules = workflowConfiguration?.workflow_scope_rules ?? [];
  const allowlisted = scopeRules.filter(
    (r: WorkflowScopeRuleOutput) => r.workflow_scope_rule_selected_mode === 'ALLOWLIST',
  );
  const denylisted = scopeRules.filter(
    (r: WorkflowScopeRuleOutput) => r.workflow_scope_rule_selected_mode === 'DENYLIST',
  );

  return (
    <>
      <ScopeColumn
        title={t('Allow list')}
        rules={allowlisted}
        resolveLabel={resolveLabel}
        resolveIcon={resolveIcon}
        onAdd={() => handleOpenDrawer('ALLOWLIST')}
        readOnly={readOnly}
        accent={theme.palette.success.main}
        headerIcon={<TaskAltOutlined fontSize="small" />}
      />

      <ScopeColumn
        title={t('Deny list')}
        rules={denylisted}
        resolveLabel={resolveLabel}
        resolveIcon={resolveIcon}
        onAdd={() => handleOpenDrawer('DENYLIST')}
        readOnly={readOnly}
        accent={theme.palette.error.main}
        headerIcon={<BlockOutlined fontSize="small" />}
        infoTooltip={t('Entries in the deny list always take priority over those in the allow list.')}
      />

      <Drawer
        open={drawerOpen}
        handleClose={handleCloseDrawer}
        title={drawerTitle}
      >
        <ScopeForm
          workflowId={workflowId}
          mode={drawerMode}
          selectedEndpointIds={selectedEndpointIds}
          selectedAssetGroupIds={selectedAssetGroupIds}
          selectedTeamIds={selectedTeamIds}
          selectedPlayerIds={selectedPlayerIds}
          selectedCustomRules={selectedCustomRules}
          initialEndpointIds={initialEndpointIds}
          initialAssetGroupIds={initialAssetGroupIds}
          initialTeamIds={initialTeamIds}
          initialPlayerIds={initialPlayerIds}
          initialCustomRules={initialCustomRules}
          onEndpointIdsChange={setSelectedEndpointIds}
          onAssetGroupIdsChange={setSelectedAssetGroupIds}
          onTeamIdsChange={setSelectedTeamIds}
          onPlayerIdsChange={setSelectedPlayerIds}
          onCustomRulesChange={setSelectedCustomRules}
          onCancel={handleCloseDrawer}
          onSubmit={handleSubmitScope}
        />
      </Drawer>
    </>
  );
};

export default ScopeRules;

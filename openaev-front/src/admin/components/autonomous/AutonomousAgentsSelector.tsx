import { AddOutlined, InfoOutlined, OpenInNewOutlined, SmartToyOutlined } from '@mui/icons-material';
import {
  Chip,
  List,
  ListItem,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  MenuItem,
  Select,
  Skeleton,
  Stack,
  Switch,
  Tooltip,
  Typography,
} from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type CSSProperties, type FunctionComponent, useMemo, useState } from 'react';

import { type AdditionalAgent, AUTONOMOUS_DISCOVERY_MODES, type AutonomousDiscoveryMode, ORCHESTRATOR_DEFAULT_DISCOVERY_MODE, SPECIALIST_DEFAULT_DISCOVERY_MODE } from '../../../actions/autonomous/autonomous-types';
import colorStyles from '../../../components/Color';
import SortHeadersComponentV2 from '../../../components/common/queryable/sort/SortHeadersComponentV2';
import { type SortHelpers } from '../../../components/common/queryable/sort/SortHelpers';
import useBodyItemsStyles from '../../../components/common/queryable/style/style';
import { type Header } from '../../../components/common/SortHeadersList';
import { useFormatter } from '../../../components/i18n';
import SearchInput from '../../../components/SearchFilter';

interface Props {
  agents: AdditionalAgent[];
  /** Ids currently enabled (consulted). The built-in is a normal member of this set. */
  enabledIds: string[];
  onToggle: (agentId: string, enabled: boolean) => void;
  /** Slug of the license-independent built-in specialist (shown with a "Built-in" chip). */
  builtinSlug: string;
  loading?: boolean;
  disabled?: boolean;
  /** External link to create a new agent in XTM One; renders an inline create row when set. */
  createAgentUrl?: string;
  /** Short helper text shown behind an "i" icon next to the title/search (keeps the surface uncluttered). */
  infoTooltip?: string;
  /**
   * Optional section title. When set, it is rendered on the same row as the search field (title left,
   * search right) so the picker owns its heading and saves the vertical space a separate subtitle
   * would take. When omitted, the search sits alone on the left (used where the page already has a
   * title, e.g. the settings breadcrumb).
   */
  title?: string;
  /** Per-agent discovery mode (agent id -> mode). When set with onModeChange, a mode selector shows. */
  modes?: Record<string, string>;
  /** Called when an agent's discovery mode changes. Enables the per-row mode selector when provided. */
  onModeChange?: (agentId: string, mode: AutonomousDiscoveryMode) => void;
  /**
   * The always-present orchestrator, pinned as a locked-on first row so "default + additional" reads
   * as one coherent roster. It cannot be toggled off; its discovery mode is still editable (keyed by
   * {@code id}, the ORCHESTRATOR_AGENT_ID sentinel) since it is the primary actor recording findings.
   */
  orchestrator?: {
    id: string;
    name: string;
    description?: string;
  };
}

// Design-system list chip (same pattern as Notifiers / the triggers list).
const chipInList: CSSProperties = {
  fontSize: 12,
  height: 20,
  borderRadius: 4,
  textTransform: 'uppercase',
  width: 100,
};

/**
 * Shared agent picker used both in Settings > Customization > Autonomous attack (tenant defaults)
 * and in the launch drawer (per-run selection). It mirrors the platform's standard list design
 * system - a design-system search field, sortable column headers, aligned body columns and the
 * shared empty-state - so it reads like every other list in the app, minus server pagination. Every
 * agent (including the built-in payload creator) is a normal toggle: built-ins are enabled by
 * default but can be turned off or replaced.
 */
const AutonomousAgentsSelector: FunctionComponent<Props> = ({
  agents,
  enabledIds,
  onToggle,
  builtinSlug,
  loading = false,
  disabled = false,
  createAgentUrl,
  infoTooltip,
  title,
  modes,
  onModeChange,
  orchestrator,
}) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const bodyItemsStyles = useBodyItemsStyles();
  const [keyword, setKeyword] = useState('');
  const [sortBy, setSortBy] = useState('agent_name');
  const [sortAsc, setSortAsc] = useState(true);

  const showModes = !!onModeChange;

  const modeLabel = (mode: AutonomousDiscoveryMode): string => {
    switch (mode) {
      case 'EXISTING_ONLY':
        return t('Existing only');
      case 'EXPANSIVE':
        return t('Expansive');
      default:
        return t('In scope');
    }
  };
  const modeHelp = (mode: AutonomousDiscoveryMode): string => {
    switch (mode) {
      case 'EXISTING_ONLY':
        return t('Only enrich assets, teams and persons that already exist and are in scope - no new ones are created.');
      case 'EXPANSIVE':
        return t('May create new assets, findings and persons anywhere - discovery can grow the perimeter (the deny-list still wins).');
      default:
        return t('May create new assets, findings and persons, but only within the run\'s allow-scope perimeter.');
    }
  };

  const renderModeSelect = (agentId: string) => {
    // The orchestrator defaults to SCOPED (it stays within the operator-defined scope); every other
    // (specialist / additional) agent defaults to EXPANSIVE (recon agents expand the perimeter).
    const fallback = orchestrator && agentId === orchestrator.id
      ? ORCHESTRATOR_DEFAULT_DISCOVERY_MODE
      : SPECIALIST_DEFAULT_DISCOVERY_MODE;
    const mode = ((modes?.[agentId] as AutonomousDiscoveryMode) ?? fallback);
    return (
      <Stack direction="row" alignItems="center" spacing={0.5}>
        <Select
          size="small"
          variant="standard"
          disableUnderline
          value={mode}
          disabled={disabled}
          onChange={event => onModeChange?.(agentId, event.target.value as AutonomousDiscoveryMode)}
          onClick={event => event.stopPropagation()}
          renderValue={value => modeLabel(value as AutonomousDiscoveryMode)}
          MenuProps={{ PaperProps: { sx: { maxWidth: 340 } } }}
          sx={{
            fontSize: 12,
            color: theme.palette.text.secondary,
          }}
          inputProps={{ 'aria-label': t('Discovery mode') }}
        >
          {AUTONOMOUS_DISCOVERY_MODES.map(m => (
            <MenuItem
              key={m}
              value={m}
              sx={{
                display: 'block',
                paddingTop: 0.75,
                paddingBottom: 0.75,
              }}
            >
              <Typography variant="body2">{modeLabel(m)}</Typography>
              <Typography
                variant="caption"
                color="text.secondary"
                sx={{
                  display: 'block',
                  whiteSpace: 'normal',
                }}
              >
                {modeHelp(m)}
              </Typography>
            </MenuItem>
          ))}
        </Select>
        <Tooltip title={modeHelp(mode)}>
          <InfoOutlined sx={{
            fontSize: 14,
            color: theme.palette.text.secondary,
            cursor: 'help',
          }}
          />
        </Tooltip>
      </Stack>
    );
  };

  const sortHelpers: SortHelpers = {
    handleSort: (field: string) => {
      if (field === sortBy) {
        setSortAsc(!sortAsc);
      } else {
        setSortBy(field);
        setSortAsc(true);
      }
    },
    handleDirectedSort: (field: string, asc: boolean) => {
      setSortBy(field);
      setSortAsc(asc);
    },
    getSortBy: () => sortBy,
    getSortAsc: () => sortAsc,
  };

  const agentName = (agent: AdditionalAgent) => agent.name ?? agent.slug ?? agent.id;

  const inlineStyles: Record<string, CSSProperties> = showModes
    ? {
        agent_name: { width: '30%' },
        agent_description: { width: '34%' },
        agent_built_in: { width: '14%' },
        agent_mode: { width: '22%' },
      }
    : {
        agent_name: { width: '32%' },
        agent_description: { width: '48%' },
        agent_built_in: { width: '20%' },
      };

  const headers: Header[] = useMemo(() => {
    const base: Header[] = [
      {
        field: 'agent_name',
        label: 'Name',
        isSortable: true,
        value: (agent: AdditionalAgent) => (
          <Typography variant="body2" sx={{ fontWeight: 500 }} noWrap>
            {agentName(agent)}
          </Typography>
        ),
      },
      {
        field: 'agent_description',
        label: 'Description',
        isSortable: false,
        value: (agent: AdditionalAgent) => (
          <Typography variant="caption" color="text.secondary" noWrap title={agent.description ?? undefined}>
            {agent.description ?? '-'}
          </Typography>
        ),
      },
      {
        field: 'agent_built_in',
        label: 'Built-in',
        isSortable: true,
        value: (agent: AdditionalAgent) => (agent.slug === builtinSlug
          ? (
              <Chip
                style={{
                  ...chipInList,
                  ...colorStyles.grey,
                }}
                label={t('Built-in')}
              />
            )
          : undefined),
      },
    ];
    if (showModes) {
      base.push({
        field: 'agent_mode',
        label: 'Discovery mode',
        isSortable: true,
        value: (agent: AdditionalAgent) => {
          const enabled = enabledIds.includes(agent.id);
          if (!enabled) {
            return (
              <Typography variant="caption" color="text.disabled">
                -
              </Typography>
            );
          }
          return renderModeSelect(agent.id);
        },
      });
    }
    return base;
  }, [showModes, enabledIds, modes, disabled, builtinSlug, theme]);

  const filtered = useMemo(() => {
    const needle = keyword.trim().toLowerCase();
    if (!needle) {
      return agents;
    }
    return agents.filter((agent) => {
      const haystack = `${agent.name ?? ''} ${agent.slug ?? ''} ${agent.description ?? ''}`.toLowerCase();
      return haystack.includes(needle);
    });
  }, [agents, keyword]);

  const sorted = useMemo(() => {
    const sortValue = (agent: AdditionalAgent): string | number => {
      switch (sortBy) {
        case 'agent_description':
          return (agent.description ?? '').toLowerCase();
        case 'agent_built_in':
          return agent.slug === builtinSlug ? 0 : 1;
        case 'agent_mode':
          return ((modes?.[agent.id] as AutonomousDiscoveryMode) ?? SPECIALIST_DEFAULT_DISCOVERY_MODE);
        default:
          return agentName(agent).toLowerCase();
      }
    };
    return [...filtered].sort((a, b) => {
      const av = sortValue(a);
      const bv = sortValue(b);
      let cmp = 0;
      if (av < bv) {
        cmp = -1;
      } else if (av > bv) {
        cmp = 1;
      }
      return sortAsc ? cmp : -cmp;
    });
  }, [filtered, sortBy, sortAsc, modes, builtinSlug]);

  // Shared header row so the loading skeleton and the loaded list line up column-for-column.
  const headerRow = (
    <ListItem
      divider={false}
      style={{
        paddingTop: 0,
        textTransform: 'uppercase',
      }}
      secondaryAction={<>&nbsp;</>}
    >
      <ListItemIcon />
      <ListItemText
        primary={(
          <SortHeadersComponentV2
            headers={headers}
            inlineStylesHeaders={inlineStyles}
            sortHelpers={sortHelpers}
          />
        )}
      />
    </ListItem>
  );

  const infoIcon = infoTooltip
    ? (
        <Tooltip title={infoTooltip}>
          <InfoOutlined fontSize="small" sx={{ color: theme.palette.text.secondary }} />
        </Tooltip>
      )
    : null;

  return (
    <div>
      <Stack
        direction="row"
        alignItems="center"
        justifyContent={title ? 'space-between' : 'flex-start'}
        spacing={1}
      >
        {title && (
          <Stack direction="row" alignItems="center" spacing={0.5}>
            <Typography variant="h2" sx={{ margin: 0 }}>{title}</Typography>
            {infoIcon}
          </Stack>
        )}
        <Stack direction="row" alignItems="center" spacing={0.5}>
          <SearchInput variant="small" onChange={value => setKeyword(value ?? '')} />
          {!title && infoIcon}
        </Stack>
      </Stack>

      {loading
        ? (
            <List>
              {headerRow}
              {['s1', 's2', 's3'].map(key => (
                <ListItem
                  key={key}
                  divider
                  secondaryAction={<Skeleton variant="rounded" width={34} height={18} />}
                >
                  <ListItemIcon>
                    <Skeleton variant="circular" width={20} height={20} />
                  </ListItemIcon>
                  <ListItemText
                    primary={(
                      <div style={bodyItemsStyles.bodyItems}>
                        {headers.map(header => (
                          <div
                            key={header.field}
                            style={{
                              ...bodyItemsStyles.bodyItem,
                              ...inlineStyles[header.field],
                            }}
                          >
                            <Skeleton variant="text" width="80%" />
                          </div>
                        ))}
                      </div>
                    )}
                  />
                </ListItem>
              ))}
            </List>
          )
        : (
            <List>
              {headerRow}

              {orchestrator && (
                <ListItem
                  divider
                  secondaryAction={(
                    <Tooltip title={t('The orchestrator is always active - it plans and drives the attack and cannot be disabled.')}>
                      <span>
                        <Switch edge="end" size="small" checked disabled inputProps={{ 'aria-label': orchestrator.name }} />
                      </span>
                    </Tooltip>
                  )}
                >
                  <ListItemIcon>
                    <SmartToyOutlined fontSize="small" sx={{ color: theme.palette.ai.main }} />
                  </ListItemIcon>
                  <ListItemText
                    primary={(
                      <div style={bodyItemsStyles.bodyItems}>
                        <div style={{
                          ...bodyItemsStyles.bodyItem,
                          ...inlineStyles.agent_name,
                        }}
                        >
                          <Typography variant="body2" sx={{ fontWeight: 500 }} noWrap>
                            {orchestrator.name}
                          </Typography>
                        </div>
                        <div style={{
                          ...bodyItemsStyles.bodyItem,
                          ...inlineStyles.agent_description,
                        }}
                        >
                          <Typography variant="caption" color="text.secondary" noWrap title={orchestrator.description}>
                            {orchestrator.description ?? '-'}
                          </Typography>
                        </div>
                        <div style={{
                          ...bodyItemsStyles.bodyItem,
                          ...inlineStyles.agent_built_in,
                        }}
                        >
                          <Chip
                            style={{
                              ...chipInList,
                              ...colorStyles.purple,
                            }}
                            label={t('Orchestrator')}
                          />
                        </div>
                        {showModes && (
                          <div style={{
                            ...bodyItemsStyles.bodyItem,
                            ...inlineStyles.agent_mode,
                          }}
                          >
                            {renderModeSelect(orchestrator.id)}
                          </div>
                        )}
                      </div>
                    )}
                  />
                </ListItem>
              )}

              {sorted.map((agent) => {
                const enabled = enabledIds.includes(agent.id);
                return (
                  <ListItem
                    key={agent.id}
                    divider
                    secondaryAction={(
                      <Switch
                        edge="end"
                        size="small"
                        checked={enabled}
                        disabled={disabled}
                        onChange={event => onToggle(agent.id, event.target.checked)}
                        inputProps={{ 'aria-label': agentName(agent) }}
                      />
                    )}
                  >
                    <ListItemIcon>
                      <SmartToyOutlined
                        fontSize="small"
                        sx={{ color: enabled ? theme.palette.ai.main : theme.palette.text.disabled }}
                      />
                    </ListItemIcon>
                    <ListItemText
                      primary={(
                        <div style={bodyItemsStyles.bodyItems}>
                          {headers.map(header => (
                            <div
                              key={header.field}
                              style={{
                                ...bodyItemsStyles.bodyItem,
                                ...inlineStyles[header.field],
                              }}
                            >
                              {header.value?.(agent)}
                            </div>
                          ))}
                        </div>
                      )}
                    />
                  </ListItem>
                );
              })}

              {sorted.length === 0 && keyword.trim() && (
                <ListItem divider>
                  <ListItemIcon />
                  <ListItemText
                    primary={(
                      <Typography variant="caption" color="text.secondary">
                        {t('No agent matches your search.')}
                      </Typography>
                    )}
                  />
                </ListItem>
              )}

              {createAgentUrl && (
                <ListItemButton
                  component="a"
                  href={createAgentUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  sx={{
                    'borderRadius': 1,
                    'marginTop': theme.spacing(0.5),
                    'color': theme.palette.ai.main,
                    '&:hover': { backgroundColor: alpha(theme.palette.ai.main, 0.08) },
                  }}
                >
                  <ListItemIcon sx={{
                    minWidth: 36,
                    color: 'inherit',
                  }}
                  >
                    <AddOutlined fontSize="small" />
                  </ListItemIcon>
                  <ListItemText
                    primary={(
                      <Stack direction="row" spacing={0.5} alignItems="center">
                        <Typography variant="body2" sx={{ color: 'inherit' }}>
                          {t('Create an agent in XTM One')}
                        </Typography>
                        <OpenInNewOutlined sx={{ fontSize: 14 }} />
                      </Stack>
                    )}
                  />
                </ListItemButton>
              )}
            </List>
          )}
    </div>
  );
};

export default AutonomousAgentsSelector;

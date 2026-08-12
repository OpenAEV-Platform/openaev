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
  useMediaQuery,
} from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type CSSProperties, type FunctionComponent, type ReactNode, useMemo, useState } from 'react';

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

  // The fixed-width, single-line column layout below needs real horizontal room (four columns plus a
  // Select and the settings side-menu). Below `md` it collapses to a stacked card per agent where the
  // name/description wrap and the toggle stays reachable, instead of truncating everything to nothing.
  const isSmall = useMediaQuery(theme.breakpoints.down('md'));

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

  // Column definitions drive ONLY the wide-screen sortable header row and column widths; the row body
  // (both layouts) is rendered by renderRow so there is a single source of truth for cell content.
  const headers: Header[] = useMemo(() => {
    const base: Header[] = [
      {
        field: 'agent_name',
        label: 'Name',
        isSortable: true,
      },
      {
        field: 'agent_description',
        label: 'Description',
        isSortable: false,
      },
      {
        field: 'agent_built_in',
        label: 'Built-in',
        isSortable: true,
      },
    ];
    if (showModes) {
      base.push({
        field: 'agent_mode',
        label: 'Discovery mode',
        isSortable: true,
      });
    }
    return base;
  }, [showModes]);

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

  const nameNode = (name: string) => (
    <Typography variant="body2" sx={{ fontWeight: 500 }} noWrap={!isSmall}>
      {name}
    </Typography>
  );
  const descriptionNode = (description?: string | null) => (
    <Typography
      variant="caption"
      color="text.secondary"
      noWrap={!isSmall}
      title={!isSmall ? (description ?? undefined) : undefined}
      sx={isSmall ? { whiteSpace: 'normal' } : undefined}
    >
      {description ?? '-'}
    </Typography>
  );

  // One row model shared by the orchestrator, every agent and both layouts, so the wide table and the
  // small-screen stack can never drift and the toggle is always rendered as the ListItem's action.
  interface RowData {
    key: string;
    name: string;
    description?: string | null;
    iconColor: string;
    chip: ReactNode;
    modeNode: ReactNode;
    trailing: ReactNode;
  }

  const renderRow = (row: RowData) => {
    const cells: {
      field: string;
      node: ReactNode;
    }[] = [
      {
        field: 'agent_name',
        node: nameNode(row.name),
      },
      {
        field: 'agent_description',
        node: descriptionNode(row.description),
      },
      {
        field: 'agent_built_in',
        node: row.chip,
      },
    ];
    if (showModes) {
      cells.push({
        field: 'agent_mode',
        node: row.modeNode,
      });
    }
    const content = isSmall
      ? (
          <Stack spacing={0.5} sx={{ minWidth: 0 }}>
            <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" sx={{ minWidth: 0 }}>
              {nameNode(row.name)}
              {row.chip}
            </Stack>
            {descriptionNode(row.description)}
            {showModes && row.modeNode ? <div>{row.modeNode}</div> : null}
          </Stack>
        )
      : (
          <div style={bodyItemsStyles.bodyItems}>
            {cells.map(cell => (
              <div
                key={cell.field}
                style={{
                  ...bodyItemsStyles.bodyItem,
                  ...inlineStyles[cell.field],
                }}
              >
                {cell.node}
              </div>
            ))}
          </div>
        );
    return (
      <ListItem
        key={row.key}
        divider
        alignItems={isSmall ? 'flex-start' : 'center'}
        secondaryAction={row.trailing}
      >
        <ListItemIcon sx={isSmall ? { marginTop: 0.5 } : undefined}>
          <SmartToyOutlined fontSize="small" sx={{ color: row.iconColor }} />
        </ListItemIcon>
        {/* disableTypography: the content is a Stack/div tree with its own Typography elements, so
            the default span wrapper would produce invalid HTML (block elements inside a span). */}
        <ListItemText disableTypography primary={content} />
      </ListItem>
    );
  };

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
              {!isSmall && headerRow}
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
                    disableTypography
                    primary={isSmall
                      ? <Skeleton variant="text" width="70%" />
                      : (
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
              {!isSmall && headerRow}

              {orchestrator && renderRow({
                key: orchestrator.id,
                name: orchestrator.name,
                description: orchestrator.description,
                iconColor: theme.palette.ai.main,
                chip: (
                  <Chip
                    style={{
                      ...chipInList,
                      ...colorStyles.purple,
                    }}
                    label={t('Orchestrator')}
                  />
                ),
                modeNode: showModes ? renderModeSelect(orchestrator.id) : null,
                trailing: (
                  <Tooltip title={t('The orchestrator is always active - it plans and drives the attack and cannot be disabled.')}>
                    <span>
                      <Switch edge="end" size="small" checked disabled inputProps={{ 'aria-label': orchestrator.name }} />
                    </span>
                  </Tooltip>
                ),
              })}

              {sorted.map((agent) => {
                const enabled = enabledIds.includes(agent.id);
                let modeNode: ReactNode = null;
                if (showModes) {
                  modeNode = enabled
                    ? renderModeSelect(agent.id)
                    : (
                        <Typography variant="caption" color="text.disabled">
                          -
                        </Typography>
                      );
                }
                return renderRow({
                  key: agent.id,
                  name: agentName(agent),
                  description: agent.description,
                  iconColor: enabled ? theme.palette.ai.main : theme.palette.text.disabled,
                  chip: agent.slug === builtinSlug
                    ? (
                        <Chip
                          style={{
                            ...chipInList,
                            ...colorStyles.grey,
                          }}
                          label={t('Built-in')}
                        />
                      )
                    : null,
                  modeNode,
                  trailing: (
                    <Switch
                      edge="end"
                      size="small"
                      checked={enabled}
                      disabled={disabled}
                      onChange={event => onToggle(agent.id, event.target.checked)}
                      inputProps={{ 'aria-label': agentName(agent) }}
                    />
                  ),
                });
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

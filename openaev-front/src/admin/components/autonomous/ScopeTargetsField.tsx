import { DnsOutlined, GroupsOutlined, HubOutlined, PersonOutlineOutlined } from '@mui/icons-material';
import { Box, Checkbox, Chip, CircularProgress, List, ListItemButton, ListItemIcon, ListItemText, Stack, Tab, Tabs, TextField, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type FunctionComponent, type ReactElement, useCallback, useEffect, useRef, useState } from 'react';

import { searchAssetGroups } from '../../../actions/asset_groups/assetgroup-action';
import { searchEndpoints } from '../../../actions/assets/endpoint-actions';
import { type AutonomousScopeTarget, type AutonomousScopeTargetType } from '../../../actions/autonomous/autonomous-types';
import { searchPlayers } from '../../../actions/players/player-actions';
import { searchTeams } from '../../../actions/teams/team-actions';
import { type Page } from '../../../components/common/queryable/Page';
import { buildSearchPagination } from '../../../components/common/queryable/QueryableUtils';
import { useFormatter } from '../../../components/i18n';
import { type AssetGroup, type EndpointOutput, type PlayerOutput, type Team } from '../../../utils/api-types';

type Option = {
  id: string;
  name: string;
};

interface KindMeta {
  label: string;
  icon: ReactElement;
  color: string;
  fetch: (query: string) => Promise<Option[]>;
}

const scopeKey = (type: AutonomousScopeTargetType, id: string) => `${type}:${id}`;

const playerLabel = (player: PlayerOutput) => {
  const full = `${player.user_firstname ?? ''} ${player.user_lastname ?? ''}`.trim();
  return full.length > 0 ? `${full} (${player.user_email})` : player.user_email;
};

interface Props {
  value: AutonomousScopeTarget[];
  onChange: (targets: AutonomousScopeTarget[]) => void;
  /** Kind to spotlight (open first) - e.g. TEAMS/PLAYERS for identity objectives. */
  emphasisType?: AutonomousScopeTargetType;
}

/**
 * Unified, multi-kind scope picker. Lets an operator compose a single scope out of the four target
 * kinds an OpenAEV inject can have - individual assets, asset groups, teams, and persons - with one
 * consistent experience: a kind switcher, a live-searched result list per kind, and a running
 * inventory of everything selected as removable chips. Emits a flat, typed list of scope targets.
 */
const ScopeTargetsField: FunctionComponent<Props> = ({ value, onChange, emphasisType }) => {
  const { t } = useFormatter();
  const theme = useTheme();

  const KIND_META: Record<AutonomousScopeTargetType, KindMeta> = {
    ASSETS: {
      label: t('Assets'),
      icon: <DnsOutlined fontSize="small" />,
      color: theme.palette.primary.main,
      fetch: query =>
        searchEndpoints(buildSearchPagination({ size: 25, textSearch: query }))
          .then((res: { data: Page<EndpointOutput> }) =>
            (res.data.content ?? []).map(endpoint => ({ id: endpoint.asset_id, name: endpoint.asset_name })))
          .catch(() => []),
    },
    ASSETS_GROUPS: {
      label: t('Asset groups'),
      icon: <HubOutlined fontSize="small" />,
      color: theme.palette.secondary.main,
      fetch: query =>
        searchAssetGroups(buildSearchPagination({ size: 25, textSearch: query }))
          .then((res: { data: Page<AssetGroup> }) =>
            (res.data.content ?? []).map(group => ({ id: group.asset_group_id, name: group.asset_group_name })))
          .catch(() => []),
    },
    TEAMS: {
      label: t('Teams'),
      icon: <GroupsOutlined fontSize="small" />,
      color: theme.palette.success.main,
      fetch: query =>
        searchTeams(buildSearchPagination({ size: 25, textSearch: query }))
          .then((res: { data: Page<Team> }) =>
            (res.data.content ?? []).map(team => ({ id: team.team_id, name: team.team_name })))
          .catch(() => []),
    },
    PLAYERS: {
      label: t('Persons'),
      icon: <PersonOutlineOutlined fontSize="small" />,
      color: theme.palette.warning.main,
      fetch: query =>
        searchPlayers(buildSearchPagination({ size: 25, textSearch: query }))
          .then((res: { data: Page<PlayerOutput> }) =>
            (res.data.content ?? []).map(player => ({ id: player.user_id, name: playerLabel(player) })))
          .catch(() => []),
    },
  };

  const kindOrder: AutonomousScopeTargetType[] = ['ASSETS', 'ASSETS_GROUPS', 'TEAMS', 'PLAYERS'];

  const [activeKind, setActiveKind] = useState<AutonomousScopeTargetType>(emphasisType ?? 'ASSETS');
  const [query, setQuery] = useState('');
  const [options, setOptions] = useState<Option[]>([]);
  const [loading, setLoading] = useState(false);
  const requestId = useRef(0);

  useEffect(() => {
    if (emphasisType) {
      setActiveKind(emphasisType);
    }
  }, [emphasisType]);

  // Live search for the active kind, debounced so typing does not flood the API.
  useEffect(() => {
    const currentRequest = ++requestId.current;
    setLoading(true);
    const handle = setTimeout(() => {
      KIND_META[activeKind].fetch(query).then((results) => {
        // Ignore out-of-order responses (a newer query/kind already superseded this one).
        if (currentRequest === requestId.current) {
          setOptions(results);
          setLoading(false);
        }
      });
    }, 250);
    return () => clearTimeout(handle);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeKind, query]);

  const selectedKeys = new Set(value.map(target => scopeKey(target.type, target.id)));

  const toggle = useCallback(
    (option: Option) => {
      const key = scopeKey(activeKind, option.id);
      if (selectedKeys.has(key)) {
        onChange(value.filter(target => scopeKey(target.type, target.id) !== key));
      } else {
        onChange([...value, { type: activeKind, id: option.id, name: option.name }]);
      }
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [activeKind, value],
  );

  const removeTarget = (target: AutonomousScopeTarget) => {
    const key = scopeKey(target.type, target.id);
    onChange(value.filter(candidate => scopeKey(candidate.type, candidate.id) !== key));
  };

  const countByKind = (kind: AutonomousScopeTargetType) => value.filter(target => target.type === kind).length;

  return (
    <Box>
      {value.length > 0 && (
        <Box
          sx={{
            display: 'flex',
            flexWrap: 'wrap',
            gap: theme.spacing(1),
            marginBottom: theme.spacing(2),
          }}
        >
          {value.map((target) => {
            const meta = KIND_META[target.type];
            return (
              <Chip
                key={scopeKey(target.type, target.id)}
                icon={meta.icon}
                label={target.name ?? target.id}
                onDelete={() => removeTarget(target)}
                size="small"
                sx={{
                  borderColor: alpha(meta.color, 0.5),
                  '& .MuiChip-icon': { color: meta.color },
                }}
                variant="outlined"
              />
            );
          })}
        </Box>
      )}

      <Tabs
        value={activeKind}
        onChange={(_, kind) => {
          setActiveKind(kind);
          setQuery('');
        }}
        variant="fullWidth"
        sx={{
          minHeight: 40,
          marginTop: theme.spacing(2),
        }}
      >
        {kindOrder.map((kind) => {
          const count = countByKind(kind);
          return (
            <Tab
              key={kind}
              value={kind}
              iconPosition="start"
              icon={KIND_META[kind].icon}
              label={count > 0 ? `${KIND_META[kind].label} (${count})` : KIND_META[kind].label}
              sx={{
                minHeight: 40,
                textTransform: 'none',
                '& .MuiTab-iconWrapper': {
                  marginBottom: 0,
                  marginRight: theme.spacing(1),
                },
              }}
            />
          );
        })}
      </Tabs>

      <TextField
        value={query}
        onChange={event => setQuery(event.target.value)}
        placeholder={`${t('Search')} ${KIND_META[activeKind].label.toLowerCase()}...`}
        size="small"
        fullWidth
        sx={{ marginTop: theme.spacing(1.5) }}
      />

      <List
        dense
        sx={{
          marginTop: theme.spacing(0.5),
          maxHeight: 220,
          overflowY: 'auto',
          border: `1px solid ${theme.palette.divider}`,
          borderRadius: 1,
        }}
      >
        {loading && (
          <Box sx={{
            display: 'flex',
            justifyContent: 'center',
            padding: theme.spacing(2),
          }}
          >
            <CircularProgress size={20} />
          </Box>
        )}
        {!loading && options.length === 0 && (
          <Typography
            variant="body2"
            color="text.secondary"
            sx={{
              padding: theme.spacing(2),
              textAlign: 'center',
            }}
          >
            {t('No match')}
          </Typography>
        )}
        {!loading
          && options.map((option) => {
            const checked = selectedKeys.has(scopeKey(activeKind, option.id));
            return (
              <ListItemButton key={option.id} onClick={() => toggle(option)} selected={checked} dense>
                <ListItemIcon sx={{ minWidth: 32 }}>
                  <Checkbox edge="start" checked={checked} tabIndex={-1} disableRipple size="small" />
                </ListItemIcon>
                <ListItemIcon sx={{
                  minWidth: 28,
                  color: KIND_META[activeKind].color,
                }}
                >
                  {KIND_META[activeKind].icon}
                </ListItemIcon>
                <ListItemText primary={option.name} />
              </ListItemButton>
            );
          })}
      </List>
    </Box>
  );
};

export default ScopeTargetsField;

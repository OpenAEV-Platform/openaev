import { AccountTreeOutlined, ArrowBackOutlined, FilterAltOffOutlined, FullscreenExitOutlined, FullscreenOutlined, HelpOutline, LocalFireDepartment, MoreHorizOutlined, SearchOutlined, TableRowsOutlined } from '@mui/icons-material';
import { Autocomplete, Box, Button, ButtonBase, ListItemButton, Paper, Popover, TextField, ToggleButton, ToggleButtonGroup, Tooltip, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type FunctionComponent, type MouseEvent, type ReactNode, useState } from 'react';

import { useFormatter } from '../../../../../components/i18n';
import type { AttackPathSimSummaryRow } from '../../../../../utils/api-types';
import { attackPathChokepointColor } from './attack-path-colors';
import { type AttackPathFreshness } from './useAttackPathLiveGraph';

// Shared height of every interactive control in the band (beacon pill, hero stats, autocompletes,
// buttons, toggles) so they all sit on one centerline: 36px is the small-ToggleButton height, the
// tallest natural "small" control, and everything else is normalized to it.
const CONTROL_HEIGHT = 36;

// One summary stat of the header (curated or data-driven from the finding types present).
export interface FindingCard {
  key: string;
  label: string;
  icon: ReactNode;
  count: number;
  hint?: string;
  /** True for a data-driven finding-type card: only the first few show inline, the rest collapse. */
  extra?: boolean;
}

// How many data-driven finding-type stats show inline before the rest collapse into one "+N types"
// stat. Every stat shares the band's width equally, so each extra one narrows all the others: past a
// handful the uppercase captions clip to a single letter ("13 C", "10 A") and the band reads as
// noise. The curated stats are never collapsed — they are the fixed vocabulary of the view.
const MAX_VISIBLE_EXTRA_CARDS = 2;

// One entry of the graph search box: an endpoint, an injector, or a finding category. Selecting one
// adapts the graph (focus an endpoint path, highlight an injector, or open a finding-type panel).
export interface SearchOption {
  kind: 'endpoint' | 'injector' | 'finding';
  label: string;
  sub?: string;
  nodeId?: string;
  ref?: string;
  card?: FindingCard;
}

interface HeroStatButtonProps {
  label: string;
  value: number;
  icon: ReactNode;
  accent: string;
  active: boolean;
  onClick: (event: MouseEvent<HTMLElement>) => void;
  labelAdornment?: ReactNode;
  hint?: string;
  hasPopup?: boolean;
}

// A clickable HeroStat (EntityDetailCommon's tinted icon box + Geologica number + uppercase
// caption), with a pressed state for the currently focused category.
const HeroStatButton: FunctionComponent<HeroStatButtonProps> = ({
  label,
  value,
  icon,
  accent,
  active,
  onClick,
  labelAdornment,
  hint,
  hasPopup = false,
}) => {
  const theme = useTheme();
  const button = (
    <ButtonBase
      onClick={onClick}
      aria-pressed={active}
      aria-haspopup={hasPopup ? 'dialog' : undefined}
      sx={{
        'display': 'flex',
        'alignItems': 'center',
        'justifyContent': 'flex-start',
        'gap': 1,
        // Stats grow to share their row evenly (so the band never reads as ragged), but keep their
        // content as flex-basis: with basis 0 they shrink indefinitely instead of wrapping, which is
        // what clipped every caption once the row got crowded — the container's flex-wrap could
        // never trigger. Now a row that no longer fits wraps to the next one.
        'flex': '1 1 auto',
        'minWidth': 0,
        'height': CONTROL_HEIGHT,
        'padding': theme.spacing(0, 1),
        'borderRadius': 1,
        'border': `1px solid ${active ? accent : 'transparent'}`,
        'backgroundColor': active ? alpha(accent, 0.08) : 'transparent',
        'transition': 'background-color 120ms, border-color 120ms',
        '&:hover': { backgroundColor: alpha(accent, 0.06) },
      }}
    >
      <Box sx={{
        'display': 'flex',
        'alignItems': 'center',
        'justifyContent': 'center',
        'width': 30,
        'height': 30,
        'borderRadius': 1,
        'flexShrink': 0,
        'color': accent,
        'background': alpha(accent, 0.1),
        'boxShadow': `inset 0 0 12px ${alpha(accent, 0.13)}`,
        '& svg': { fontSize: 16 },
      }}
      >
        {icon}
      </Box>
      <Box sx={{
        minWidth: 0,
        textAlign: 'left',
      }}
      >
        <Typography sx={{
          fontFamily: '"Geologica", sans-serif',
          fontSize: 18,
          fontWeight: 500,
          lineHeight: 1.05,
          color: 'text.primary',
        }}
        >
          {value}
        </Typography>
        <Typography
          component="div"
          sx={{
            display: 'flex',
            alignItems: 'center',
            gap: 0.5,
            minWidth: 0,
            fontSize: 9.5,
            fontWeight: 600,
            letterSpacing: '0.07em',
            textTransform: 'uppercase',
            color: 'text.secondary',
            whiteSpace: 'nowrap',
            // Equal-width slots can be narrower than a long caption on a small screen: clip it rather
            // than letting it bleed past the card edge.
            overflow: 'hidden',
            textOverflow: 'ellipsis',
          }}
        >
          {label}
          {labelAdornment}
        </Typography>
      </Box>
    </ButtonBase>
  );
  return hint ? <Tooltip title={hint}>{button}</Tooltip> : button;
};

interface Props {
  // Simulation picker (scenario context only).
  showPicker: boolean;
  pickerOptions: AttackPathSimSummaryRow[];
  selectedRow: AttackPathSimSummaryRow | null;
  labelFor: (simId?: string) => string;
  onSimulationChange: (simulationId: string) => void;
  // Focus escapes.
  hasCardFocus: boolean;
  onClearFocus: () => void;
  hasPathFocus: boolean;
  onClearPathFocus: () => void;
  // Freshness beacon (live / reconnecting / finished).
  freshness: AttackPathFreshness;
  freshnessLabel: string;
  freshnessTitle: string;
  // Summary stats.
  cards: FindingCard[];
  activeCard: string | null;
  onCardClick: (card: FindingCard) => void;
  /** 0 hides the chokepoint stat (also hidden in the focused finding-path view). */
  chokepointCount: number;
  chokepointOpen: boolean;
  onChokepointClick: () => void;
  // Graph / table toggle.
  view: 'graph' | 'table';
  onViewChange: (view: 'graph' | 'table') => void;
  // Fullscreen toggle.
  fullscreen: boolean;
  onToggleFullscreen: () => void;
  // Search (endpoint / injector / finding type).
  searchOptions: SearchOption[];
  searchInput: string;
  onSearchInputChange: (value: string) => void;
  onSearchSelect: (option: SearchOption | null) => void;
  searchGroupLabel: (kind: SearchOption['kind']) => string;
}

// The single header band over the attack-path canvas: live beacon and clickable summary stats on
// the left, search and the view/fullscreen segmented controls on the right, with the simulation
// picker and focus escapes when relevant. One Paper — no loose second toolbar row.
const AttackPathHeader: FunctionComponent<Props> = ({
  showPicker,
  pickerOptions,
  selectedRow,
  labelFor,
  onSimulationChange,
  hasCardFocus,
  onClearFocus,
  hasPathFocus,
  onClearPathFocus,
  freshness,
  freshnessLabel,
  freshnessTitle,
  cards,
  activeCard,
  onCardClick,
  chokepointCount,
  chokepointOpen,
  onChokepointClick,
  view,
  onViewChange,
  fullscreen,
  onToggleFullscreen,
  searchOptions,
  searchInput,
  onSearchInputChange,
  onSearchSelect,
  searchGroupLabel,
}) => {
  const theme = useTheme();
  const { t } = useFormatter();
  const chokepointColor = attackPathChokepointColor(theme);
  // Only two rendered states: live (green) and reconnecting (amber) — the pill is not shown at all
  // once the run is finished.
  const beaconColor = freshness === 'reconnecting' ? theme.palette.warning.main : theme.palette.success.main;
  // Anchor of the collapsed finding-types popover (null = closed).
  const [moreAnchor, setMoreAnchor] = useState<HTMLElement | null>(null);
  const moreOpen = !!moreAnchor;

  // Split the stats: everything curated plus the first few finding-type extras stay inline, the rest
  // collapse behind a single "+N types" stat opening a popover. The extras arrive sorted by count, so
  // what stays inline is what matters most; the collapsed ones remain reachable from the search box.
  const shown = cards.filter(c => c.count > 0);
  const inlineCards = shown.filter(c => !c.extra);
  const extraCards = shown.filter(c => c.extra);
  const visibleExtras = extraCards.slice(0, MAX_VISIBLE_EXTRA_CARDS);
  const collapsedExtras = extraCards.slice(MAX_VISIBLE_EXTRA_CARDS);
  const collapsedTotal = collapsedExtras.reduce((s, c) => s + c.count, 0);
  const collapsedActive = collapsedExtras.some(c => c.key === activeCard);

  return (
    <Paper
      variant="outlined"
      sx={{
        display: 'flex',
        alignItems: 'center',
        flexWrap: 'wrap',
        columnGap: 1.5,
        rowGap: 0.75,
        padding: theme.spacing(0.75, 1.5),
        borderRadius: 1,
        background: freshness === 'live'
          ? `linear-gradient(135deg, ${alpha(theme.palette.success.main, 0.05)}, transparent 55%)`
          : undefined,
      }}
    >
      {showPicker && (
        <Autocomplete
          size="small"
          options={pickerOptions}
          value={selectedRow}
          isOptionEqualToValue={(o, v) => o.simulationId === v.simulationId}
          getOptionLabel={o => labelFor(o.simulationId)}
          onChange={(_, v) => {
            if (v?.simulationId) {
              onSimulationChange(v.simulationId);
            }
          }}
          renderOption={(props, o) => {
            const { key, ...rest } = props as { key: string } & Record<string, unknown>;
            return (
              <Box
                component="li"
                key={key}
                {...rest}
                sx={{
                  display: 'flex',
                  gap: 1,
                }}
              >
                <span>{labelFor(o.simulationId)}</span>
                <Typography
                  component="span"
                  variant="caption"
                  color="text.secondary"
                  sx={{ marginLeft: 'auto' }}
                >
                  {`${o.endpointCount ?? 0} ${t('endpoints')} · ${o.executionCount ?? 0} ${t('exec.')}`}
                </Typography>
              </Box>
            );
          }}
          renderInput={params => <TextField {...params} label={t('Simulation')} />}
          sx={{
            'width': 300,
            '& .MuiOutlinedInput-root': {
              height: CONTROL_HEIGHT,
              paddingBlock: 0,
            },
          }}
        />
      )}

      {/* Focus escapes read as actions (buttons with a directional icon), not deletable chips. */}
      {hasPathFocus && (
        <Button
          size="small"
          variant="outlined"
          startIcon={<ArrowBackOutlined />}
          onClick={onClearPathFocus}
          sx={{
            flexShrink: 0,
            height: CONTROL_HEIGHT,
          }}
        >
          {t('Back to full graph')}
        </Button>
      )}
      {hasCardFocus && (
        <Button
          size="small"
          variant="outlined"
          color="inherit"
          startIcon={<FilterAltOffOutlined />}
          onClick={onClearFocus}
          sx={{
            flexShrink: 0,
            height: CONTROL_HEIGHT,
            color: 'text.secondary',
            borderColor: 'divider',
          }}
        >
          {t('Clear focus')}
        </Button>
      )}

      {/* Live beacon (ExecutionHero's status pill). Only for transient states (live / reconnecting):
          once the run is over, the page hero already says "Finished" — repeating it here would be a
          duplicate. */}
      {freshness !== 'finished' && (
        <Tooltip title={freshnessTitle}>
          <Box
            role="status"
            sx={{
              display: 'flex',
              alignItems: 'center',
              gap: 1,
              height: CONTROL_HEIGHT,
              padding: theme.spacing(0, 1.5),
              borderRadius: 1,
              border: `1px solid ${alpha(beaconColor, 0.3)}`,
              backgroundColor: alpha(beaconColor, 0.08),
              flexShrink: 0,
            }}
          >
            <Box sx={{
              width: 8,
              height: 8,
              borderRadius: '50%',
              backgroundColor: beaconColor,
              ...(freshness === 'live'
                ? {
                    'animation': 'attack-path-beacon 2s ease-out infinite',
                    '@keyframes attack-path-beacon': {
                      '0%': { boxShadow: `0 0 0 0 ${alpha(beaconColor, 0.5)}` },
                      '100%': { boxShadow: `0 0 0 7px ${alpha(beaconColor, 0)}` },
                    },
                    '@media (prefers-reduced-motion: reduce)': { animation: 'none' },
                  }
                : {}),
            }}
            />
            <Typography sx={{
              fontFamily: '"Geologica", sans-serif',
              fontWeight: 600,
              fontSize: 11,
              letterSpacing: '0.12em',
              textTransform: 'uppercase',
              color: beaconColor,
              whiteSpace: 'nowrap',
            }}
            >
              {freshnessLabel}
            </Typography>
          </Box>
        </Tooltip>
      )}

      {/* Clickable summary stats, hairline-separated (HeroStats language). */}
      <Box sx={{
        'display': 'flex',
        'alignItems': 'center',
        'flexWrap': 'wrap',
        'flex': 1,
        'minWidth': 0,
        'columnGap': 1,
        'rowGap': 0.5,
        // Hairline separators between stats: a short, vertically centered rule rather than a
        // full-height border, so wrapped rows never show floor-to-ceiling dividers.
        '& > *:not(:last-child)': {
          'position': 'relative',
          'paddingRight': 1,
          '&::after': {
            content: '""',
            position: 'absolute',
            right: 0,
            top: '50%',
            transform: 'translateY(-50%)',
            width: '1px',
            height: 16,
            backgroundColor: alpha(theme.palette.text.primary, 0.08),
          },
        },
      }}
      >
        {[...inlineCards, ...visibleExtras].map(c => (
          <HeroStatButton
            key={c.key}
            label={c.label}
            value={c.count}
            icon={c.icon}
            accent={theme.palette.primary.main}
            active={activeCard === c.key}
            onClick={() => onCardClick(c)}
            hint={c.hint}
          />
        ))}
        {/* The finding types that did not fit: one stat opening a popover, so the band stays legible
            however many types a simulation produces. */}
        {collapsedExtras.length > 0 && (
          <HeroStatButton
            label={`${collapsedExtras.length} ${t('types')}`}
            value={collapsedTotal}
            icon={<MoreHorizOutlined fontSize="small" />}
            accent={theme.palette.primary.main}
            active={collapsedActive || moreOpen}
            onClick={e => setMoreAnchor(e.currentTarget)}
            hasPopup
            hint={t('Other finding types discovered in this simulation. Click to list them.')}
          />
        )}
        {/* Top chokepoints: the most-exposed endpoints. Violet accent (off the verdict scale) and a
            ranked, clickable explainer dialog owned by the container. */}
        {chokepointCount > 0 && (
          <HeroStatButton
            label={t('Top chokepoints')}
            value={chokepointCount}
            icon={<LocalFireDepartment fontSize="small" />}
            accent={chokepointColor}
            active={chokepointOpen}
            onClick={onChokepointClick}
            hasPopup
            labelAdornment={(
              <Tooltip
                arrow
                title={t('Chokepoints rank endpoints by findings weighted by criticality (score = findings × criticality weight), so the top one is the most findings on the most critical endpoint. Click to see how it is computed.')}
              >
                <HelpOutline sx={{
                  fontSize: 13,
                  color: 'text.disabled',
                  flexShrink: 0,
                }}
                />
              </Tooltip>
            )}
          />
        )}
      </Box>

      {/* Collapsed finding types: same click behaviour as an inline stat (focus the graph on that
          type and open its panel), just listed instead of squeezed into the band. */}
      <Popover
        open={moreOpen}
        anchorEl={moreAnchor}
        onClose={() => setMoreAnchor(null)}
        anchorOrigin={{
          vertical: 'bottom',
          horizontal: 'left',
        }}
        slotProps={{
          paper: {
            sx: {
              marginTop: 0.5,
              minWidth: 240,
              maxHeight: 320,
            },
          },
        }}
      >
        <Typography sx={{
          padding: theme.spacing(1, 1.5, 0.5),
          fontSize: 9.5,
          fontWeight: 600,
          letterSpacing: '0.07em',
          textTransform: 'uppercase',
          color: 'text.secondary',
        }}
        >
          {t('Other types')}
        </Typography>
        {collapsedExtras.map(c => (
          <ListItemButton
            key={c.key}
            selected={activeCard === c.key}
            onClick={() => {
              onCardClick(c);
              setMoreAnchor(null);
            }}
            sx={{
              display: 'flex',
              alignItems: 'center',
              gap: 1,
              paddingBlock: 0.5,
            }}
          >
            <Box sx={{
              'display': 'flex',
              'color': 'text.secondary',
              '& svg': { fontSize: 16 },
            }}
            >
              {c.icon}
            </Box>
            <Typography variant="body2" noWrap sx={{ flex: 1 }}>{c.label}</Typography>
            <Typography
              variant="body2"
              sx={{
                fontFamily: '"Geologica", sans-serif',
                fontVariantNumeric: 'tabular-nums',
                color: 'text.secondary',
              }}
            >
              {c.count}
            </Typography>
          </ListItemButton>
        ))}
      </Popover>

      <Autocomplete<SearchOption>
        size="small"
        options={searchOptions}
        value={null}
        inputValue={searchInput}
        onInputChange={(_, v) => onSearchInputChange(v)}
        onChange={(_, v) => {
          onSearchSelect(v);
          onSearchInputChange('');
        }}
        blurOnSelect
        clearOnBlur
        groupBy={o => searchGroupLabel(o.kind)}
        getOptionLabel={o => o.label}
        isOptionEqualToValue={(o, v) => o.nodeId === v.nodeId && o.label === v.label}
        filterOptions={(opts, state) => {
          const q = state.inputValue.trim().toLowerCase();
          if (!q) {
            return opts;
          }
          return opts.filter(o => o.label.toLowerCase().includes(q) || (o.sub ?? '').toLowerCase().includes(q));
        }}
        renderOption={(props, o) => {
          const { key, ...rest } = props as { key: string } & Record<string, unknown>;
          return (
            <li key={key} {...rest}>
              <Box sx={{ minWidth: 0 }}>
                <Typography variant="body2" noWrap>{o.label}</Typography>
                {o.sub && <Typography variant="caption" color="text.secondary" noWrap sx={{ display: 'block' }}>{o.sub}</Typography>}
              </Box>
            </li>
          );
        }}
        renderInput={params => (
          <TextField
            {...params}
            placeholder={t('Search endpoint, injector, finding…')}
            InputProps={{
              ...params.InputProps,
              startAdornment: (
                <SearchOutlined
                  fontSize="small"
                  sx={{
                    mr: 0.5,
                    color: 'text.secondary',
                  }}
                />
              ),
            }}
          />
        )}
        sx={{
          'width': 240,
          '& .MuiOutlinedInput-root': {
            height: CONTROL_HEIGHT,
            paddingBlock: 0,
          },
        }}
      />
      <ToggleButtonGroup
        size="small"
        exclusive
        value={view}
        onChange={(_, v) => v && onViewChange(v)}
        aria-label={t('View')}
        sx={{ '& .MuiToggleButton-root': { height: CONTROL_HEIGHT } }}
      >
        <ToggleButton value="graph" aria-label={t('Graph')}>
          <Tooltip title={t('Graph')}><AccountTreeOutlined fontSize="small" /></Tooltip>
        </ToggleButton>
        <ToggleButton value="table" aria-label={t('Table')}>
          <Tooltip title={t('Table')}><TableRowsOutlined fontSize="small" /></Tooltip>
        </ToggleButton>
      </ToggleButtonGroup>
      {/* Standalone ToggleButton so fullscreen reads as part of the same segmented family. */}
      <ToggleButton
        size="small"
        value="fullscreen"
        selected={fullscreen}
        onChange={onToggleFullscreen}
        aria-label={fullscreen ? t('Exit fullscreen') : t('Fullscreen')}
        sx={{ height: CONTROL_HEIGHT }}
      >
        <Tooltip title={fullscreen ? t('Exit fullscreen') : t('Fullscreen')}>
          {fullscreen ? <FullscreenExitOutlined fontSize="small" /> : <FullscreenOutlined fontSize="small" />}
        </Tooltip>
      </ToggleButton>
    </Paper>
  );
};

export default AttackPathHeader;

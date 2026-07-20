import { Box, MenuItem, Select, ToggleButton, ToggleButtonGroup, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type FunctionComponent, memo, useCallback, useMemo } from 'react';
import { makeStyles } from 'tss-react/mui';
import { useLocalStorage } from 'usehooks-ts';

import type { AttackPatternHelper } from '../../../../../../actions/attack_patterns/attackpattern-helper';
import type { KillChainPhaseHelper } from '../../../../../../actions/kill_chain_phases/killchainphase-helper';
import { useFormatter } from '../../../../../../components/i18n';
import { useHelper } from '../../../../../../store';
import {
  type AttackPattern,
  type EsSeries,
  type KillChainPhase,
  type StructuralHistogramWidget,
} from '../../../../../../utils/api-types';
import { sortKillChainPhase } from '../../../../../../utils/kill_chain_phases/kill_chain_phases';
import ColoredPercentageRate from './components/ColoredPercentageRate';
import KillChainPhaseColumn from './KillChainPhaseColumn';
import { buildKillChainPhaseIndex, getCoverageAccent, resolvedData } from './securityCoverageUtils';

const useStyles = makeStyles()(theme => ({
  container: {
    flex: 1,
    overflow: 'auto',
    display: 'flex',
    gap: theme.spacing(1),
    paddingRight: theme.spacing(1),
  },
}));

/** Coverage-based technique filter for the matrix. */
export type CoverageFilter = 'all' | 'covered' | 'gaps';

interface Props {
  widgetId: string;
  widgetConfig: StructuralHistogramWidget;
  data: EsSeries[];
  /**
   * Overview mode: focus the matrix on covered techniques only. Forces the
   * coverage scope to "covered", hides the All/Covered/Gaps segmented control and
   * the coverage KPI. Used by the scenario / simulation overviews where the
   * matrix is a result view, not a coverage-planning tool.
   */
  coveredOnly?: boolean;
}

// Well-known kill chain identifiers get their official product name; anything
// else (custom kill chains) falls back to the raw name from the data.
const KILL_CHAIN_LABELS: Record<string, string> = {
  'mitre-attack': 'MITRE ATT&CK',
  'mitre-atlas': 'MITRE ATLAS',
};

const killChainLabel = (name: string) => KILL_CHAIN_LABELS[name.toLowerCase()] ?? name;

// Shared overline label style for the header sections (heading font, uppercase).
const OVERLINE_SX = {
  fontFamily: '"Geologica", sans-serif',
  fontWeight: 600,
  fontSize: 10,
  letterSpacing: '0.12em',
  textTransform: 'uppercase' as const,
  color: 'text.secondary',
  lineHeight: 1,
} as const;

const SecurityCoverageContent: FunctionComponent<Props> = ({ widgetId, widgetConfig, data, coveredOnly = false }) => {
  // Standard hooks
  const { classes } = useStyles();
  const theme = useTheme();
  const { t } = useFormatter();

  // Fetching data - use stable selector
  const { attackPatternMap, killChainPhaseMap }: {
    attackPatternMap: Record<string, AttackPattern>;
    killChainPhaseMap: Record<string, KillChainPhase>;
  } = useHelper((helper: AttackPatternHelper & KillChainPhaseHelper) => ({
    attackPatternMap: helper.getAttackPatternsMap(),
    killChainPhaseMap: helper.getKillChainPhasesMap(),
  }));

  // Memoize resolved data computations
  const resolvedDataSuccess = useMemo(
    () => resolvedData(attackPatternMap, killChainPhaseMap, data.at(0)?.data ?? []),
    [attackPatternMap, killChainPhaseMap, data],
  );

  const resolvedDataFailure = useMemo(
    () => resolvedData(attackPatternMap, killChainPhaseMap, data.at(1)?.data ?? []),
    [attackPatternMap, killChainPhaseMap, data],
  );

  // Build indexes for fast phase-based filtering - O(n) once instead of O(n) per phase
  const successByPhase = useMemo(
    () => buildKillChainPhaseIndex(resolvedDataSuccess),
    [resolvedDataSuccess],
  );

  const failureByPhase = useMemo(
    () => buildKillChainPhaseIndex(resolvedDataFailure),
    [resolvedDataFailure],
  );

  // Memoize sorted kill chain phases
  const sortedPhases = useMemo(
    () => Object.values(killChainPhaseMap).toSorted(sortKillChainPhase),
    [killChainPhaseMap],
  );

  // Distinct kill chains present on the platform (e.g. MITRE ATT&CK + MITRE ATLAS).
  // The matrix shows ONE kill chain at a time; mixing their phases side by side
  // duplicated column names and interleaved unrelated techniques.
  const killChains = useMemo(
    () => [...new Set(sortedPhases.map(phase => phase.phase_kill_chain_name))].sort((a, b) => a.localeCompare(b)),
    [sortedPhases],
  );
  const defaultKillChain = useMemo(
    () => killChains.find(chain => chain.toLowerCase().includes('attack')) ?? killChains[0],
    [killChains],
  );
  const [selectedKillChain, setSelectedKillChain] = useLocalStorage<string | null>('widget-' + widgetId + '-kill-chain', null);
  // A stored selection that no longer matches any kill chain falls back to the default.
  const activeKillChain = selectedKillChain != null && killChains.includes(selectedKillChain)
    ? selectedKillChain
    : defaultKillChain;

  const visiblePhases = useMemo(
    () => sortedPhases.filter(phase => phase.phase_kill_chain_name === activeKillChain),
    [sortedPhases, activeKillChain],
  );

  const [storedCoverageFilter, setCoverageFilter] = useLocalStorage<CoverageFilter>('widget-' + widgetId + '-coverage-filter', 'all');
  // Overview mode is locked to "covered": the matrix is a result view there.
  const coverageFilter: CoverageFilter = coveredOnly ? 'covered' : storedCoverageFilter;

  const handleCoverageFilterChange = useCallback(
    (_: React.MouseEvent<HTMLElement>, value: CoverageFilter | null) => {
      if (value != null) setCoverageFilter(value);
    },
    [setCoverageFilter],
  );

  // Coverage KPIs for the active kill chain: how many top-level techniques exist,
  // how many have been exercised (any success/failure), and the overall success
  // rate across exercised techniques. Drives the header summary + the gaps count.
  const coverageStats = useMemo(() => {
    const phaseIds = new Set(visiblePhases.map(phase => phase.phase_id));
    const techniques = Object.values(attackPatternMap).filter(
      ap => ap.attack_pattern_parent === null
        && ap.attack_pattern_kill_chain_phases?.some(id => phaseIds.has(id)),
    );
    const techniqueExternalIds = new Set(
      techniques.map(ap => ap.attack_pattern_external_id).filter(Boolean) as string[],
    );
    const covered = new Set<string>();
    let success = 0;
    let failure = 0;
    for (const d of resolvedDataSuccess) {
      if (d.attack_pattern_external_id && techniqueExternalIds.has(d.attack_pattern_external_id)) {
        covered.add(d.attack_pattern_external_id);
        success += d.value ?? 0;
      }
    }
    for (const d of resolvedDataFailure) {
      if (d.attack_pattern_external_id && techniqueExternalIds.has(d.attack_pattern_external_id)) {
        covered.add(d.attack_pattern_external_id);
        failure += d.value ?? 0;
      }
    }
    const total = techniqueExternalIds.size;
    const coveredCount = covered.size;
    return {
      total,
      covered: coveredCount,
      gaps: total - coveredCount,
      coverageRate: total > 0 ? coveredCount / total : 0,
      successRate: success + failure > 0 ? success / (success + failure) : null,
    };
  }, [attackPatternMap, visiblePhases, resolvedDataSuccess, resolvedDataFailure]);

  const coveragePct = Math.round(coverageStats.coverageRate * 100);
  const coverageAccent = getCoverageAccent(coverageStats.total > 0 ? coverageStats.coverageRate : null);

  return (
    <Box
      flex={1}
      display="flex"
      flexDirection="column"
      minHeight={0}
      height="100%"
    >
      <Box
        component="header"
        className="noDrag"
        sx={{
          display: 'flex',
          flexWrap: 'wrap',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 2,
          padding: theme.spacing(1.5, 2),
          marginBottom: 2,
          borderRadius: 1,
          border: `1px solid ${theme.palette.divider}`,
          background: `linear-gradient(135deg, ${alpha(theme.palette.primary.main, 0.06)} 0%, ${alpha(theme.palette.background.paper, 0.4)} 60%)`,
        }}
      >
        {/* Left cluster: kill chain selector + coverage-scope segmented control */}
        <Box sx={{
          display: 'flex',
          alignItems: 'flex-end',
          gap: 2,
          flexWrap: 'wrap',
        }}
        >
          {killChains.length > 0 && (
            <Box sx={{
              display: 'flex',
              flexDirection: 'column',
              gap: 0.75,
            }}
            >
              <Typography sx={OVERLINE_SX}>{t('Kill chain')}</Typography>
              <Select
                size="small"
                variant="standard"
                disableUnderline
                value={activeKillChain ?? ''}
                onChange={e => setSelectedKillChain(e.target.value)}
                sx={{
                  'minWidth': 170,
                  'fontFamily': '"Geologica", sans-serif',
                  'fontWeight': 600,
                  'fontSize': 15,
                  '& .MuiSelect-select': { paddingBottom: 0 },
                }}
              >
                {killChains.map(chain => (
                  <MenuItem key={chain} value={chain}>{killChainLabel(chain)}</MenuItem>
                ))}
              </Select>
            </Box>
          )}
          {!coveredOnly && (
            <Box sx={{
              display: 'flex',
              flexDirection: 'column',
              gap: 0.75,
            }}
            >
              <Typography sx={OVERLINE_SX}>{t('techniques')}</Typography>
              <ToggleButtonGroup
                exclusive
                size="small"
                value={coverageFilter}
                onChange={handleCoverageFilterChange}
                sx={{
                  '& .MuiToggleButton-root': {
                    paddingY: 0.25,
                    paddingX: 1.25,
                    textTransform: 'none',
                  },
                }}
              >
                <ToggleButton value="all">{t('All')}</ToggleButton>
                <ToggleButton value="covered">
                  {t('Covered')}
                  <Box
                    component="span"
                    sx={{
                      marginLeft: 0.75,
                      color: 'text.secondary',
                    }}
                  >
                    {coverageStats.covered}
                  </Box>
                </ToggleButton>
                <ToggleButton value="gaps">
                  {t('Gaps')}
                  <Box
                    component="span"
                    sx={{
                      marginLeft: 0.75,
                      color: 'text.secondary',
                    }}
                  >
                    {coverageStats.gaps}
                  </Box>
                </ToggleButton>
              </ToggleButtonGroup>
            </Box>
          )}
        </Box>
        {/* Right cluster: coverage KPI + success-rate legend */}
        <Box sx={{
          display: 'flex',
          alignItems: 'center',
          gap: 3,
          flexWrap: 'wrap',
        }}
        >
          {!coveredOnly && (
            <Box sx={{
              display: 'flex',
              flexDirection: 'column',
              gap: 0.75,
              minWidth: 132,
            }}
            >
              <Typography sx={OVERLINE_SX}>{t('Coverage')}</Typography>
              <Box sx={{
                display: 'flex',
                alignItems: 'baseline',
                gap: 0.75,
              }}
              >
                <Typography sx={{
                  fontFamily: '"Geologica", sans-serif',
                  fontWeight: 600,
                  fontSize: 18,
                  lineHeight: 1,
                  color: coverageAccent,
                }}
                >
                  {coveragePct}
                  %
                </Typography>
                <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                  {t('{covered}/{total} tested', {
                    covered: coverageStats.covered,
                    total: coverageStats.total,
                  })}
                </Typography>
              </Box>
              <Box sx={{
                height: 4,
                borderRadius: 2,
                backgroundColor: alpha(theme.palette.text.primary, 0.08),
                overflow: 'hidden',
              }}
              >
                <Box sx={{
                  width: `${coveragePct}%`,
                  height: '100%',
                  borderRadius: 2,
                  backgroundColor: coverageAccent,
                }}
                />
              </Box>
            </Box>
          )}
          <Box sx={{
            display: 'flex',
            flexDirection: 'column',
            gap: 0.75,
          }}
          >
            <Typography sx={OVERLINE_SX}>{t('Success rate')}</Typography>
            <ColoredPercentageRate />
          </Box>
        </Box>
      </Box>
      <Box className={classes.container}>
        {/* Every column hides itself when the coverage scope leaves it empty;
            without this guard a 0-covered / 0-gaps scope renders a blank body. */}
        {((coverageFilter === 'covered' && coverageStats.covered === 0)
          || (coverageFilter === 'gaps' && coverageStats.gaps === 0)) ? (
              <Typography
                variant="body2"
                sx={{
                  margin: 'auto',
                  color: 'text.secondary',
                }}
              >
                {t('No data to display')}
              </Typography>
            ) : visiblePhases.map((phase) => {
              // Use indexed lookups - O(1) instead of O(n) filter
              const resolvedDataSuccessByKillChainPhase = successByPhase.get(phase.phase_external_id) ?? [];
              const resolvedDataFailureByKillChainPhase = failureByPhase.get(phase.phase_external_id) ?? [];
              return (
                <KillChainPhaseColumn
                  key={phase.phase_id}
                  killChainPhase={phase}
                  coverageFilter={coverageFilter}
                  resolvedDataSuccess={resolvedDataSuccessByKillChainPhase}
                  resolvedDataFailure={resolvedDataFailureByKillChainPhase}
                  widgetId={widgetId}
                  widgetConfig={widgetConfig}
                />
              );
            })}
      </Box>
    </Box>
  );
};

export default memo(SecurityCoverageContent);

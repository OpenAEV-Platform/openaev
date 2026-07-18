import { Box, Checkbox, FormControl, FormControlLabel, InputLabel, MenuItem, Select, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, memo, useCallback, useId, useMemo } from 'react';
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
import { buildKillChainPhaseIndex, resolvedData } from './securityCoverageUtils';

const useStyles = makeStyles()(theme => ({
  container: {
    flex: 1,
    overflow: 'auto',
    display: 'flex',
    gap: theme.spacing(1),
    paddingRight: theme.spacing(1),
  },
}));

interface Props {
  widgetId: string;
  widgetConfig: StructuralHistogramWidget;
  data: EsSeries[];
}

// Well-known kill chain identifiers get their official product name; anything
// else (custom kill chains) falls back to the raw name from the data.
const KILL_CHAIN_LABELS: Record<string, string> = {
  'mitre-attack': 'MITRE ATT&CK',
  'mitre-atlas': 'MITRE ATLAS',
};

const killChainLabel = (name: string) => KILL_CHAIN_LABELS[name.toLowerCase()] ?? name;

const SecurityCoverageContent: FunctionComponent<Props> = ({ widgetId, widgetConfig, data }) => {
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

  const [showCoveredOnly, setShowCoveredOnly] = useLocalStorage<boolean>('widget-' + widgetId, false);

  const handleShowCoveredOnlyChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => setShowCoveredOnly(e.target.checked),
    [setShowCoveredOnly],
  );

  const killChainSelectLabelId = useId();

  // Memoize container padding style
  const headerStyle = useMemo(() => ({
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    gap: theme.spacing(2),
    padding: theme.spacing(1),
  }), [theme]);

  return (
    <Box
      flex={1}
      display="flex"
      flexDirection="column"
      minHeight={0}
      height="100%"
    >
      <div style={headerStyle}>
        <Box
          className="noDrag"
          sx={{
            display: 'flex',
            alignItems: 'center',
            gap: 2,
          }}
        >
          {killChains.length > 0 && (
            <FormControl size="small" sx={{ minWidth: 200 }}>
              <InputLabel id={killChainSelectLabelId}>{t('Kill chain')}</InputLabel>
              <Select
                labelId={killChainSelectLabelId}
                label={t('Kill chain')}
                value={activeKillChain ?? ''}
                onChange={e => setSelectedKillChain(e.target.value)}
              >
                {killChains.map(chain => (
                  <MenuItem key={chain} value={chain}>{killChainLabel(chain)}</MenuItem>
                ))}
              </Select>
            </FormControl>
          )}
          <FormControlLabel
            sx={{ marginLeft: 0 }}
            control={(
              <Checkbox
                size="small"
                checked={showCoveredOnly}
                onChange={handleShowCoveredOnlyChange}
                color="primary"
              />
            )}
            label={<Typography variant="body2">{t('Show covered TTP only')}</Typography>}
          />
        </Box>
        <div>
          <ColoredPercentageRate />
        </div>
      </div>
      <Box className={classes.container}>
        {visiblePhases.map((phase) => {
          // Use indexed lookups - O(1) instead of O(n) filter
          const resolvedDataSuccessByKillChainPhase = successByPhase.get(phase.phase_external_id) ?? [];
          const resolvedDataFailureByKillChainPhase = failureByPhase.get(phase.phase_external_id) ?? [];
          return (
            <KillChainPhaseColumn
              key={phase.phase_id}
              killChainPhase={phase}
              showCoveredOnly={showCoveredOnly}
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

import { ButtonBase, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type FunctionComponent, useEffect, useMemo } from 'react';

import { type AttackPatternHelper } from '../../../../actions/attack_patterns/attackpattern-helper';
import { type InjectorContractHelper } from '../../../../actions/injector_contracts/injector-contract-helper';
import { fetchInjectorsContracts } from '../../../../actions/InjectorContracts';
import { type KillChainPhaseHelper } from '../../../../actions/kill_chain_phases/killchainphase-helper';
import { type FilterHelpers } from '../../../../components/common/queryable/filter/FilterHelpers';
import { buildEmptyFilter } from '../../../../components/common/queryable/filter/FilterUtils';
import { useFormatter } from '../../../../components/i18n';
import { useHelper } from '../../../../store';
import { type AttackPattern, type KillChainPhase } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import KillChainSelect from './KillChainSelect';
import useKillChains from './useKillChains';

interface InjectorContractLight {
  injector_contract_id: string;
  injector_contract_attack_patterns_external_id?: string[];
}

export const MITRE_FILTER_KEY = 'injector_contract_attack_patterns';

// External ids belonging to a top-level technique: the technique itself plus its
// sub-techniques (T1595 -> [T1595, T1595.001, ...]). Both the count and the
// applied filter use this set, so a click always returns exactly what the tile
// count advertises (previously the filter sent only the parent id and returned
// nothing when contracts were tagged with sub-techniques).
const relevantExternalIds = (technique: AttackPattern, all: AttackPattern[]): string[] => {
  const parent = technique.attack_pattern_external_id;
  const subs = all
    .filter(ap => ap.attack_pattern_external_id.startsWith(`${parent}.`))
    .map(ap => ap.attack_pattern_external_id);
  return [parent, ...subs];
};

interface PhaseColumnProps {
  killChainPhase: KillChainPhase;
  techniques: AttackPattern[];
  countFor: (technique: AttackPattern) => number;
  onTechniqueClick: (technique: AttackPattern) => void;
}

const PhaseColumn: FunctionComponent<PhaseColumnProps> = ({
  killChainPhase,
  techniques,
  countFor,
  onTechniqueClick,
}) => {
  const theme = useTheme();
  const { t } = useFormatter();
  const accent = theme.palette.primary.main;

  return (
    <div style={{
      minWidth: 232,
      flex: '0 0 232px',
      display: 'flex',
      flexDirection: 'column',
      gap: theme.spacing(1),
    }}
    >
      <div style={{ textAlign: 'center' }}>
        <Typography sx={{
          fontFamily: '"Geologica", sans-serif',
          fontSize: 12,
          fontWeight: 600,
          lineHeight: 1.3,
        }}
        >
          {killChainPhase.phase_name}
        </Typography>
        <Typography sx={{
          fontSize: 10.5,
          color: 'text.secondary',
          whiteSpace: 'nowrap',
        }}
        >
          {t('{count} techniques', { count: techniques.length })}
        </Typography>
      </div>
      <div style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 4,
      }}
      >
        {techniques.map((technique) => {
          const count = countFor(technique);
          const disabled = count === 0;
          return (
            <ButtonBase
              key={technique.attack_pattern_id}
              disabled={disabled}
              onClick={() => onTechniqueClick(technique)}
              sx={{
                'display': 'flex',
                'alignItems': 'center',
                'justifyContent': 'space-between',
                'gap': 1,
                'width': '100%',
                'textAlign': 'left',
                'padding': theme.spacing(0.75, 1),
                'borderRadius': 1,
                'border': `1px solid ${alpha(theme.palette.text.primary, 0.08)}`,
                'backgroundColor': theme.palette.background.paper,
                'opacity': disabled ? 0.45 : 1,
                'transition': 'background-color 0.15s ease, border-color 0.15s ease',
                '&:hover': {
                  borderColor: alpha(accent, 0.5),
                  backgroundColor: alpha(accent, 0.08),
                },
              }}
            >
              <Typography sx={{
                fontSize: 12,
                lineHeight: 1.3,
                minWidth: 0,
                overflow: 'hidden',
                display: '-webkit-box',
                WebkitLineClamp: 2,
                WebkitBoxOrient: 'vertical',
              }}
              >
                <span style={{ color: theme.palette.text.secondary }}>
                  {`[${technique.attack_pattern_external_id}] `}
                </span>
                {technique.attack_pattern_name}
              </Typography>
              {count > 0 && (
                <span style={{
                  flexShrink: 0,
                  fontSize: 11,
                  fontWeight: 600,
                  lineHeight: '18px',
                  minWidth: 22,
                  textAlign: 'center',
                  padding: theme.spacing(0, 0.5),
                  borderRadius: 4,
                  color: accent,
                  backgroundColor: alpha(accent, 0.14),
                }}
                >
                  {count}
                </span>
              )}
            </ButtonBase>
          );
        })}
      </div>
    </div>
  );
};

interface MitreFilterProps {
  helpers: FilterHelpers;
  onClick: (attackPatternId: string) => void;
  defaultSelectedAttackPatternIds?: string[];
  className?: string;
  /**
   * Externally controlled kill chain (e.g. selector rendered in the drawer header via
   * KillChainSelect). When provided, the matrix body renders columns only - no selector chrome.
   */
  killChain?: string;
}

const MitreFilter: FunctionComponent<MitreFilterProps> = ({
  helpers,
  onClick,
  className = '',
  killChain,
}) => {
  const theme = useTheme();
  const dispatch = useAppDispatch();

  const { attackPatterns, killChainPhases, injectorsContracts } = useHelper(
    (helper: AttackPatternHelper & KillChainPhaseHelper & InjectorContractHelper) => ({
      attackPatterns: helper.getAttackPatterns(),
      killChainPhases: helper.getKillChainPhases(),
      injectorsContracts: helper.getInjectorContracts(),
    }),
  );
  useDataLoader(() => {
    dispatch(fetchInjectorsContracts());
  });

  useEffect(() => {
    helpers.handleAddFilterWithEmptyValue(buildEmptyFilter(MITRE_FILTER_KEY, 'contains'));
  }, []);

  const { killChains, activeKillChain: internalKillChain, selectKillChain } = useKillChains();
  const activeKillChain = killChain ?? internalKillChain;

  const phasesOfActiveKillChain = useMemo(
    () => killChainPhases
      .filter((p: KillChainPhase) => p.phase_kill_chain_name === activeKillChain)
      .sort((k1: KillChainPhase, k2: KillChainPhase) => (k1.phase_order ?? 0) - (k2.phase_order ?? 0)),
    [killChainPhases, activeKillChain],
  );

  const techniquesFor = (killChainPhase: KillChainPhase): AttackPattern[] =>
    attackPatterns
      .filter((ap: AttackPattern) => ap.attack_pattern_kill_chain_phases?.includes(killChainPhase.phase_id))
      .filter((ap: AttackPattern) => !ap.attack_pattern_external_id.includes('.'))
      .sort((a: AttackPattern, b: AttackPattern) => a.attack_pattern_name.localeCompare(b.attack_pattern_name));

  const countFor = (technique: AttackPattern): number => {
    const externalIds = relevantExternalIds(technique, attackPatterns);
    return injectorsContracts.filter((c: InjectorContractLight) =>
      externalIds.some(id => c.injector_contract_attack_patterns_external_id?.includes(id)),
    ).length;
  };

  const onTechniqueClick = (technique: AttackPattern) => {
    // Replace (not merge) so each click filters by exactly one technique + its
    // sub-techniques, matching the tile count. `contains` + mode `or` matches a
    // contract tagged with ANY of those external ids.
    helpers.handleRemoveFilterByKey(MITRE_FILTER_KEY);
    helpers.handleAddFilter({
      key: MITRE_FILTER_KEY,
      operator: 'contains',
      values: relevantExternalIds(technique, attackPatterns),
      mode: 'or',
    });
    onClick(technique.attack_pattern_id);
  };

  return (
    <div
      className={className}
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: theme.spacing(2),
      }}
    >
      {/* Fallback selector for call sites that don't host it in the drawer header.
          Externally controlled matrices keep the body free of chrome. */}
      {killChain == null && (
        <KillChainSelect
          killChains={killChains}
          value={activeKillChain}
          onChange={selectKillChain}
        />
      )}
      <div style={{
        display: 'flex',
        gap: theme.spacing(1.5),
        alignItems: 'flex-start',
      }}
      >
        {phasesOfActiveKillChain.map((killChainPhase: KillChainPhase) => (
          <PhaseColumn
            key={killChainPhase.phase_id}
            killChainPhase={killChainPhase}
            techniques={techniquesFor(killChainPhase)}
            countFor={countFor}
            onTechniqueClick={onTechniqueClick}
          />
        ))}
      </div>
    </div>
  );
};

export default MitreFilter;

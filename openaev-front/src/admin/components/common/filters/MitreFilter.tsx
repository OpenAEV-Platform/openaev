import { ListItemButton, ListItemText } from '@mui/material';
import { type FunctionComponent, useEffect, useMemo, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

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

interface InjectorContractLight {
  injector_contract_id: string;
  injector_contract_attack_patterns_external_id?: string[];
}

const useStyles = makeStyles()(theme => ({
  container: {
    display: 'flex',
    gap: 10,
  },
  button: {
    whiteSpace: 'nowrap',
    width: '100%',
    textTransform: 'capitalize',
    borderRadius: 4,
    border: `1px solid ${theme.palette.action.selected}`,
    fontSize: theme.typography.subtitle2.fontSize,
  },
}));

export const MITRE_FILTER_KEY = 'injector_contract_attack_patterns';

interface KillChainPhaseComponentProps {
  killChainPhase: KillChainPhase;
  attackPatterns: AttackPattern[];
  injectorsContratLight: InjectorContractLight[];
  selectedAttackPatternIds?: Set<string>;
  onAttackPatternClick: (attackPattern: AttackPattern) => void;
}

const KillChainPhaseColumn: FunctionComponent<KillChainPhaseComponentProps> = ({
  killChainPhase,
  attackPatterns,
  injectorsContratLight,
  selectedAttackPatternIds = new Set(),
  onAttackPatternClick,
}) => {
  // Standard hooks
  const { t } = useFormatter();
  const { classes } = useStyles();
  const [selectedIds, setSelectedIds] = useState(selectedAttackPatternIds);

  // Computing sorted techniques with concise filtering
  const sortedTechniques: AttackPattern[] = attackPatterns
    .filter(({ attack_pattern_external_id }) => !attack_pattern_external_id.includes('.'))
    .sort((attackPattern1: AttackPattern, attackPattern2: AttackPattern) =>
      attackPattern1.attack_pattern_name.localeCompare(attackPattern2.attack_pattern_name),
    );

  const getInjectorsContractsLengthByAttackPattern = (attackPattern: AttackPattern) => {
    const subTechnique = attackPatterns.filter(value => value.attack_pattern_external_id.includes(attackPattern.attack_pattern_external_id));
    const externalIds = subTechnique.map(value => value.attack_pattern_external_id);
    externalIds.push(attackPattern.attack_pattern_external_id);
    const injectorsContratList = injectorsContratLight
      .filter(value => externalIds.some(value1 => value.injector_contract_attack_patterns_external_id?.includes(value1)));
    return injectorsContratList.length || 0;
  };

  const onItemClick = (attackPattern: AttackPattern) => {
    setSelectedIds((prevSelectedIds) => {
      const newSelectedIds = new Set(prevSelectedIds);
      if (newSelectedIds.has(attackPattern.attack_pattern_id)) {
        newSelectedIds.delete(attackPattern.attack_pattern_id);
      } else {
        newSelectedIds.add(attackPattern.attack_pattern_id);
      }
      return newSelectedIds;
    });
    onAttackPatternClick(attackPattern);
  };

  return (
    <div>
      <div style={{
        marginBottom: 10,
        textAlign: 'center',
      }}
      >
        <div>{killChainPhase.phase_name}</div>
        <div style={{ textWrap: 'nowrap' }}>
          (
          {sortedTechniques.length}
          {' '}
          {t('techniques')}
          )
        </div>
      </div>
      <div>
        {sortedTechniques.map((attackPattern) => {
          const numberOfPayloads = getInjectorsContractsLengthByAttackPattern(attackPattern);
          return (
            <ListItemButton
              key={attackPattern.attack_pattern_id}
              selected={selectedIds.has(attackPattern.attack_pattern_id)}
              className={classes.button}
              component="div"
              dense
              onClick={() => onItemClick(attackPattern)}
            >
              <ListItemText
                primary={
                  `[${attackPattern.attack_pattern_external_id}] ${attackPattern.attack_pattern_name} ${numberOfPayloads > 0 ? `(${numberOfPayloads})` : ''}`
                }
              />
            </ListItemButton>
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
}

const MitreFilter: FunctionComponent<MitreFilterProps> = ({
  helpers,
  onClick,
  defaultSelectedAttackPatternIds = [],
  className = '',
}) => {
  // Standard hooks
  const { classes } = useStyles();
  const dispatch = useAppDispatch();

  // Fetching data
  const { attackPatterns, killChainPhases, injectorsContracts } = useHelper((helper: AttackPatternHelper & KillChainPhaseHelper & InjectorContractHelper) => ({
    attackPatterns: helper.getAttackPatterns(),
    killChainPhases: helper.getKillChainPhases(),
    injectorsContracts: helper.getInjectorContracts(),
  }));
  useDataLoader(() => {
    dispatch(fetchInjectorsContracts());
  });

  // Filters
  useEffect(() => {
    helpers.handleAddFilterWithEmptyValue(buildEmptyFilter(MITRE_FILTER_KEY, 'contains'));
  }, []);

  // Kill Chain Phase
  const sortedKillChainPhases = useMemo(() => {
    return killChainPhases.sort((k1: KillChainPhase, k2: KillChainPhase) => {
      return (k1.phase_order ?? 0) - (k2.phase_order ?? 0);
    });
  }, []);

  // Attack Pattern
  const getAttackPatterns = (killChainPhase: KillChainPhase) => {
    return attackPatterns.filter((attackPattern: AttackPattern) => attackPattern.attack_pattern_kill_chain_phases?.includes(killChainPhase.phase_id));
  };

  const onAttackPatternClick = (attackPattern: AttackPattern) => {
    helpers.handleAddSingleValueFilter(
      MITRE_FILTER_KEY,
      attackPattern.attack_pattern_external_id,
    );
    onClick(attackPattern.attack_pattern_id);
  };

  return (
    <div className={`${classes.container} ${className}`}>
      {sortedKillChainPhases
        .map((killChainPhase: KillChainPhase) => (
          <KillChainPhaseColumn
            key={killChainPhase.phase_id}
            killChainPhase={killChainPhase}
            attackPatterns={getAttackPatterns(killChainPhase)}
            injectorsContratLight={injectorsContracts}
            onAttackPatternClick={id => onAttackPatternClick(id)}
            selectedAttackPatternIds={new Set(defaultSelectedAttackPatternIds)}
          />
        ))}
    </div>
  );
};
export default MitreFilter;

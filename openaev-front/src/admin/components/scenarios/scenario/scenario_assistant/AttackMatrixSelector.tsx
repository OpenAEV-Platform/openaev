import { CheckOutlined } from '@mui/icons-material';
import { Box, Tooltip, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type FunctionComponent, useMemo } from 'react';

import { type AttackPatternHelper } from '../../../../../actions/attack_patterns/attackpattern-helper';
import { type InjectorContractHelper } from '../../../../../actions/injector_contracts/injector-contract-helper';
import { type KillChainPhaseHelper } from '../../../../../actions/kill_chain_phases/killchainphase-helper';
import EllipsisTooltip from '../../../../../components/common/EllipsisTooltip';
import { useFormatter } from '../../../../../components/i18n';
import { useHelper } from '../../../../../store';
import { type AttackPattern, type KillChainPhase } from '../../../../../utils/api-types';

interface InjectorContractLight {
  injector_contract_id: string;
  injector_contract_attack_patterns_external_id?: string[];
}

interface Props {
  selectedIds: string[];
  onToggle: (attackPatternId: string) => void;
  /** Free-text filter applied to technique id + name. */
  search?: string;
  /** Active kill chain (MITRE ATT&CK, MITRE ATLAS, ...); shows every phase when absent. */
  killChain?: string;
  /** When true, only techniques covered by at least one action are shown. */
  onlyWithArsenal?: boolean;
}

// A controlled attack matrix: one column per kill-chain phase of the active
// kill chain, one selectable tile per technique. Mirrors the marketplace card
// language (accent border + tint on selection, hover lift) so the assistant
// reads like the rest of the redesigned platform. Selection state is owned by
// the parent.
const AttackMatrixSelector: FunctionComponent<Props> = ({
  selectedIds,
  onToggle,
  search = '',
  killChain,
  onlyWithArsenal = false,
}) => {
  const theme = useTheme();
  const { t } = useFormatter();
  const accent = theme.palette.primary.main;

  const { attackPatterns, killChainPhases, injectorsContracts } = useHelper(
    (helper: AttackPatternHelper & KillChainPhaseHelper & InjectorContractHelper) => ({
      attackPatterns: helper.getAttackPatterns(),
      killChainPhases: helper.getKillChainPhases(),
      injectorsContracts: helper.getInjectorContracts() as InjectorContractLight[],
    }),
  );

  const selected = useMemo(() => new Set(selectedIds), [selectedIds]);
  const normalizedSearch = search.trim().toLowerCase();

  const sortedKillChainPhases = useMemo<KillChainPhase[]>(
    () => (killChainPhases as KillChainPhase[])
      .filter((p: KillChainPhase) => !killChain || p.phase_kill_chain_name === killChain)
      .sort((k1: KillChainPhase, k2: KillChainPhase) => (k1.phase_order ?? 0) - (k2.phase_order ?? 0)),
    [killChainPhases, killChain],
  );

  // Action count per technique (technique + its sub-techniques), used as a
  // "coverage" hint so users prefer TTPs that already have actions available
  // to generate injects from.
  const arsenalCountByExternalId = useMemo(() => {
    const counts = new Map<string, number>();
    const parents = attackPatterns.filter(
      (ap: AttackPattern) => !ap.attack_pattern_external_id.includes('.'),
    );
    parents.forEach((parent: AttackPattern) => {
      const familyExternalIds = attackPatterns
        .filter((ap: AttackPattern) => ap.attack_pattern_external_id.includes(parent.attack_pattern_external_id))
        .map((ap: AttackPattern) => ap.attack_pattern_external_id);
      familyExternalIds.push(parent.attack_pattern_external_id);
      const count = injectorsContracts.filter(
        (contract: InjectorContractLight) => familyExternalIds.some(
          (externalId: string) => contract.injector_contract_attack_patterns_external_id?.includes(externalId),
        ),
      ).length;
      counts.set(parent.attack_pattern_external_id, count);
    });
    return counts;
  }, [attackPatterns, injectorsContracts]);

  const columns = useMemo(() => sortedKillChainPhases.map((phase: KillChainPhase) => {
    const techniques = (attackPatterns as AttackPattern[])
      .filter((ap: AttackPattern) => ap.attack_pattern_kill_chain_phases?.includes(phase.phase_id))
      .filter((ap: AttackPattern) => !ap.attack_pattern_external_id.includes('.'))
      .filter((ap: AttackPattern) => {
        if (!onlyWithArsenal) return true;
        return (arsenalCountByExternalId.get(ap.attack_pattern_external_id) ?? 0) > 0;
      })
      .filter((ap: AttackPattern) => {
        if (!normalizedSearch) return true;
        return `${ap.attack_pattern_external_id} ${ap.attack_pattern_name}`.toLowerCase().includes(normalizedSearch);
      })
      .sort((a: AttackPattern, b: AttackPattern) => a.attack_pattern_name.localeCompare(b.attack_pattern_name));
    return {
      phase,
      techniques,
    };
  }).filter(column => column.techniques.length > 0), [sortedKillChainPhases, attackPatterns, normalizedSearch, onlyWithArsenal, arsenalCountByExternalId]);

  if (columns.length === 0) {
    return (
      <Box sx={{
        padding: 4,
        textAlign: 'center',
        color: 'text.secondary',
      }}
      >
        <Typography variant="body2">{t('No technique matches your search.')}</Typography>
      </Box>
    );
  }

  return (
    <Box sx={{
      display: 'flex',
      gap: 1.5,
      overflowX: 'auto',
      paddingBottom: 1,
    }}
    >
      {columns.map(({ phase, techniques }) => {
        const selectedInColumn = techniques.filter(ap => selected.has(ap.attack_pattern_id)).length;
        return (
          <Box
            key={phase.phase_id}
            sx={{
              minWidth: 190,
              flex: '1 0 190px',
              display: 'flex',
              flexDirection: 'column',
              gap: 0.75,
            }}
          >
            <Box sx={{
              position: 'sticky',
              top: 0,
              zIndex: 1,
              paddingBottom: 0.5,
              backgroundColor: theme.palette.background.paper,
            }}
            >
              <Typography sx={{
                fontFamily: theme.typography.h1.fontFamily,
                fontWeight: 600,
                fontSize: 11,
                letterSpacing: '0.08em',
                textTransform: 'uppercase',
                color: 'text.primary',
                lineHeight: 1.3,
              }}
              >
                <EllipsisTooltip>{phase.phase_name}</EllipsisTooltip>
              </Typography>
              <Typography sx={{
                fontSize: 10.5,
                color: 'text.secondary',
              }}
              >
                {selectedInColumn > 0
                  ? t('{selected}/{total} selected', {
                      selected: selectedInColumn,
                      total: techniques.length,
                    })
                  : t('{count} techniques', { count: techniques.length })}
              </Typography>
            </Box>
            <Box sx={{
              display: 'flex',
              flexDirection: 'column',
              gap: 0.5,
            }}
            >
              {techniques.map((technique: AttackPattern) => {
                const isSelected = selected.has(technique.attack_pattern_id);
                const arsenalCount = arsenalCountByExternalId.get(technique.attack_pattern_external_id) ?? 0;
                return (
                  <Box
                    key={technique.attack_pattern_id}
                    role="checkbox"
                    aria-checked={isSelected}
                    aria-label={`[${technique.attack_pattern_external_id}] ${technique.attack_pattern_name}`}
                    tabIndex={0}
                    onClick={() => onToggle(technique.attack_pattern_id)}
                    onKeyDown={(event) => {
                      if (event.key === 'Enter' || event.key === ' ') {
                        event.preventDefault();
                        onToggle(technique.attack_pattern_id);
                      }
                    }}
                    sx={{
                      'position': 'relative',
                      'cursor': 'pointer',
                      'padding': theme.spacing(0.75, 1),
                      'paddingRight': 3,
                      'borderRadius': 1,
                      'border': '1px solid',
                      'borderColor': isSelected ? alpha(accent, 0.6) : theme.palette.divider,
                      'backgroundColor': isSelected ? alpha(accent, 0.12) : theme.palette.background.default,
                      'transition': theme.transitions.create(['background-color', 'border-color', 'transform']),
                      '&:hover': {
                        borderColor: alpha(accent, 0.4),
                        backgroundColor: isSelected ? alpha(accent, 0.16) : theme.palette.action.hover,
                      },
                      '&:focus-visible': {
                        outline: `2px solid ${accent}`,
                        outlineOffset: -1,
                      },
                    }}
                  >
                    <Tooltip title={technique.attack_pattern_name} enterDelay={500}>
                      <Typography sx={{
                        fontSize: 11.5,
                        fontWeight: isSelected ? 600 : 500,
                        lineHeight: 1.3,
                        color: 'text.primary',
                        display: '-webkit-box',
                        WebkitLineClamp: 2,
                        WebkitBoxOrient: 'vertical',
                        overflow: 'hidden',
                      }}
                      >
                        {technique.attack_pattern_name}
                      </Typography>
                    </Tooltip>
                    <Box sx={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 0.5,
                      marginTop: 0.25,
                    }}
                    >
                      <Typography sx={{
                        fontSize: 9.5,
                        fontFamily: 'monospace',
                        color: 'text.secondary',
                        flex: 1,
                        minWidth: 0,
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                      }}
                      >
                        {technique.attack_pattern_external_id}
                      </Typography>
                      {arsenalCount > 0 && (
                        <Tooltip title={t('{count} actions available', { count: arsenalCount })}>
                          <Box
                            component="span"
                            sx={{
                              flexShrink: 0,
                              fontSize: 9.5,
                              fontWeight: 600,
                              lineHeight: '16px',
                              minWidth: 18,
                              textAlign: 'center',
                              paddingInline: 0.5,
                              borderRadius: '4px',
                              color: accent,
                              backgroundColor: alpha(accent, 0.14),
                            }}
                          >
                            {arsenalCount}
                          </Box>
                        </Tooltip>
                      )}
                    </Box>
                    {isSelected && (
                      <Box sx={{
                        position: 'absolute',
                        top: 6,
                        right: 6,
                        width: 15,
                        height: 15,
                        borderRadius: 0.5,
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        backgroundColor: accent,
                        color: theme.palette.primary.contrastText,
                      }}
                      >
                        <CheckOutlined sx={{ fontSize: 11 }} />
                      </Box>
                    )}
                  </Box>
                );
              })}
            </Box>
          </Box>
        );
      })}
    </Box>
  );
};

export default AttackMatrixSelector;

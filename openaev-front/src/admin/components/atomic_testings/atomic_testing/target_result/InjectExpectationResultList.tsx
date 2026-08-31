import { InfoOutlined } from '@mui/icons-material';
import { Box, Tooltip, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { useContext } from 'react';
import { useNavigate } from 'react-router';

import ButtonPopover from '../../../../../components/common/ButtonPopover';
import { useFormatter } from '../../../../../components/i18n';
import ItemSecurityPlatformType from '../../../../../components/ItemSecurityPlatformType';
import { SECURITY_PLATFORM_BASE_URL } from '../../../../../constants/BaseUrls';
import {
  type Inject,
  type InjectExpectationOutput,
  type InjectExpectationResult,
  type PayloadSimple,
} from '../../../../../utils/api-types';
import { useAbility } from '../../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../../utils/permissions/types';
import { isNotEmptyField } from '../../../../../utils/utils';
import { type InjectExpectationsStore } from '../../../common/injects/expectations/Expectation';
import InjectIcon from '../../../common/injects/InjectIcon';
import InjectExpectationContext from '../context/InjectExpectationContext';
import StatusPill from './StatusPill';
import TargetResultAlertNumber from './TargetResultAlertNumber';
import useExpectationSourceLogo from './useExpectationSourceLogo';

// One agent's contribution to an aggregated (endpoint-level) security-platform
// result. Surfaced in the per-line "i" tooltip so the endpoint view keeps the
// per-agent detail without the heavy expandable per-agent tables.
export interface AgentResultBreakdownEntry {
  agentName: string;
  result: string;
  score?: number;
  date?: string;
}

interface Props {
  injectExpectation: InjectExpectationsStore;
  injectExpectationResults: InjectExpectationResult[];
  injectExpectationAgent: InjectExpectationOutput['inject_expectation_agent'];
  injectorContractPayload?: PayloadSimple;
  injectType: Inject['inject_type'];
  // When set (endpoint aggregation), each source id/name maps to the per-agent
  // results that rolled up into the aggregated row, shown in an "i" tooltip.
  agentBreakdownBySource?: Record<string, AgentResultBreakdownEntry[]>;
  // Endpoint (asset) aggregated view: alerts live on the child agent expectations,
  // so there is no per-line agent, but the count/dialog endpoints roll them up from
  // the asset expectation id. Let the Alerts column render without an agent scope.
  aggregateAgentAlerts?: boolean;
}

const GRID_TEMPLATE_COLUMNS = 'minmax(180px, 2fr) 150px 140px 160px 80px 40px';
// Sum of the column minimums (750px) + 5 column gaps (8px each) + right
// padding: anything smaller lets the grid tracks overflow the row box on small
// screens, so the hover background would stop before the actions column.
const ROW_MIN_WIDTH = 800;

const InjectExpectationResultList = ({
  injectExpectation,
  injectExpectationResults,
  injectExpectationAgent,
  injectorContractPayload,
  injectType,
  agentBreakdownBySource,
  aggregateAgentAlerts = false,
}: Props) => {
  const { nsdt, t } = useFormatter();
  const theme = useTheme();
  const navigate = useNavigate();

  const renderBreakdownTooltip = (entries: AgentResultBreakdownEntry[]) => (
    <Box sx={{
      paddingBlock: 0.5,
      minWidth: 300,
    }}
    >
      <Typography sx={{
        fontSize: 11,
        textTransform: 'uppercase',
        letterSpacing: '0.08em',
        color: 'text.secondary',
        fontFamily: theme.typography.h1.fontFamily,
        marginBottom: 0.5,
      }}
      >
        {t('Per-agent results')}
      </Typography>
      {entries.map((entry, entryIndex) => (
        <Box
          key={`${entry.agentName}-${entryIndex}`}
          sx={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: 1.5,
            paddingBlock: 0.5,
            ...(entryIndex > 0 && { borderTop: `1px solid ${alpha(theme.palette.common.white, 0.12)}` }),
          }}
        >
          <Typography sx={{
            fontSize: 12,
            minWidth: 0,
            whiteSpace: 'nowrap',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
          }}
          >
            {entry.agentName}
          </Typography>
          <div style={{
            display: 'flex',
            alignItems: 'center',
            gap: theme.spacing(1),
            flexShrink: 0,
          }}
          >
            <StatusPill label={entry.result ? t(entry.result) : '-'} status={entry.result} />
            <Typography sx={{
              fontSize: 11,
              color: 'text.secondary',
              fontVariantNumeric: 'tabular-nums',
              whiteSpace: 'nowrap',
            }}
            >
              {entry.date ? nsdt(entry.date) : '-'}
            </Typography>
          </div>
        </Box>
      ))}
    </Box>
  );

  const { onOpenDeleteInjectExpectationResult, onOpenEditInjectExpectationResultResult, onOpenAlertsDialog } = useContext(InjectExpectationContext);

  const ability = useAbility();
  const canPivotToSecurityPlatform = ability.can(ACTIONS.ACCESS, SUBJECTS.SECURITY_PLATFORMS);
  // Platform-first icon and pivot resolution: results written by a since-deleted
  // collector still resolve to their (surviving) security platform.
  const { resolveSecurityPlatformId, resolveLogoSrc, onLogoError } = useExpectationSourceLogo();

  const getAvatar = (expectationResult: InjectExpectationResult) => {
    const logoSrc = resolveLogoSrc(expectationResult);
    if (logoSrc) {
      return (
        <img
          src={logoSrc}
          alt={expectationResult.sourceName ?? expectationResult.sourceId}
          onError={onLogoError}
          style={{
            width: 18,
            height: 18,
            borderRadius: 4,
          }}
        />
      );
    }

    return (
      <InjectIcon
        isPayload={isNotEmptyField(injectorContractPayload)}
        type={injectorContractPayload
          ? injectorContractPayload.payload_collector_type
          ?? injectorContractPayload.payload_type
          : injectType}
      />
    );
  };

  const headerSx = {
    fontSize: 11,
    textTransform: 'uppercase',
    letterSpacing: '0.08em',
    color: 'text.secondary',
    fontFamily: theme.typography.h1.fontFamily,
    whiteSpace: 'nowrap',
  } as const;

  return (
    <div style={{
      overflowX: 'auto',
      marginTop: theme.spacing(2),
    }}
    >
      <Box sx={{ minWidth: ROW_MIN_WIDTH }}>
        <Box
          sx={{
            display: 'grid',
            gridTemplateColumns: GRID_TEMPLATE_COLUMNS,
            alignItems: 'center',
            columnGap: 1,
            paddingRight: 1,
          }}
        >
          <Typography sx={headerSx}>{t('Security platforms')}</Typography>
          <Typography sx={headerSx}>{t('Type')}</Typography>
          <Typography sx={headerSx}>{t('Status')}</Typography>
          <Typography sx={headerSx}>{t('Detection time')}</Typography>
          <Typography sx={headerSx}>{t('Alerts')}</Typography>
          <span aria-hidden />
        </Box>

        {injectExpectationResults.map((expectationResult, index) => {
          // Result labels are written with inconsistent casing across producers (backend
          // ExpectationType says "Partially vulnerable", the frontend status map "Partially
          // Vulnerable"), so match them case-insensitively.
          const resultLabel = expectationResult.result?.toLowerCase();
          const showDetectionTime = resultLabel === 'prevented' || resultLabel === 'detected' || resultLabel === 'success'
            // Vulnerability verdicts (e.g. written by a scanner platform like Nuclei) carry the scan time.
            || resultLabel === 'not vulnerable' || resultLabel === 'vulnerable' || resultLabel === 'partially vulnerable';
          // Alerts apply to a collector result that detected/prevented. They render either
          // for a per-agent row (injectExpectationAgent set) or for the aggregated endpoint
          // row, where the backend rolls the child agents' traces up onto the asset
          // expectation id. Anything else (no collector match, non-collector source) shows a dash.
          const alertsApplicable = !!expectationResult.sourceId
            && expectationResult.sourceType === 'collector'
            && (resultLabel === 'prevented' || resultLabel === 'detected');
          const alertsInScope = !!injectExpectationAgent || aggregateAgentAlerts;
          const showAlerts = alertsApplicable && alertsInScope;
          const showAlertsDash = !showAlerts;

          const sourceName = expectationResult.sourceName?.trim() || '-';
          const breakdownKey = expectationResult.sourceId ?? expectationResult.sourceName ?? '';
          const agentBreakdown = breakdownKey ? agentBreakdownBySource?.[breakdownKey] : undefined;
          const platformType = expectationResult.sourcePlatform?.trim();
          const securityPlatformId = canPivotToSecurityPlatform ? resolveSecurityPlatformId(expectationResult) : undefined;

          const cellSx = {
            display: 'flex',
            alignItems: 'center',
            minWidth: 0,
          } as const;

          // Whole line pivots to the security platform overview (no per-title
          // hyperlink); the alerts count keeps its own click target.
          const handleRowClick = () => {
            if (securityPlatformId) {
              navigate(`${SECURITY_PLATFORM_BASE_URL}/${securityPlatformId}`);
            }
          };

          return (
            // Each row is its own grid container (same column template as the
            // header) so hover and click cover the full line, like any list item.
            <Box
              key={`${expectationResult.sourceName}-${index}`}
              onClick={handleRowClick}
              sx={{
                display: 'grid',
                gridTemplateColumns: GRID_TEMPLATE_COLUMNS,
                alignItems: 'center',
                columnGap: 1,
                // Breathing room so the actions popover (and its hover circle)
                // never sits flush against the right edge of the hovered line.
                paddingRight: 1,
                minHeight: 44,
                borderTop: `1px solid ${alpha(theme.palette.text.primary, 0.08)}`,
                cursor: securityPlatformId ? 'pointer' : 'default',
                ...(securityPlatformId && { '&:hover': { backgroundColor: alpha(theme.palette.text.primary, 0.04) } }),
              }}
            >
              <Box sx={cellSx}>
                <div style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: theme.spacing(1),
                  minWidth: 0,
                }}
                >
                  <Box
                    aria-hidden
                    sx={{
                      width: 28,
                      height: 28,
                      flexShrink: 0,
                      borderRadius: 1,
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      backgroundColor: alpha(theme.palette.text.primary, 0.04),
                    }}
                  >
                    {getAvatar(expectationResult)}
                  </Box>
                  <Typography
                    sx={{
                      fontSize: 13,
                      minWidth: 0,
                      whiteSpace: 'nowrap',
                      overflow: 'hidden',
                      textOverflow: 'ellipsis',
                    }}
                  >
                    {sourceName}
                  </Typography>
                  {agentBreakdown && agentBreakdown.length > 0 && (
                    <Tooltip
                      title={renderBreakdownTooltip(agentBreakdown)}
                      arrow
                      slotProps={{ tooltip: { sx: { maxWidth: 480 } } }}
                    >
                      <InfoOutlined
                        sx={{
                          'fontSize': 15,
                          'flexShrink': 0,
                          'color': 'text.secondary',
                          'cursor': 'help',
                          '&:hover': { color: 'text.primary' },
                        }}
                        onClick={e => e.stopPropagation()}
                      />
                    </Tooltip>
                  )}
                </div>
              </Box>
              <Box sx={cellSx}>
                {platformType
                  ? <ItemSecurityPlatformType type={platformType} />
                  : (
                      <Typography sx={{
                        fontSize: 13,
                        color: 'text.disabled',
                      }}
                      >
                        -
                      </Typography>
                    )}
              </Box>
              <Box sx={cellSx}>
                {expectationResult.result && (
                  <StatusPill
                    label={t(expectationResult.result)}
                    status={expectationResult.result}
                  />
                )}
              </Box>
              <Box sx={cellSx}>
                <Typography sx={{
                  fontSize: 13,
                  fontVariantNumeric: 'tabular-nums',
                }}
                >
                  {showDetectionTime ? nsdt(expectationResult.date) : '-'}
                </Typography>
              </Box>
              <Box sx={cellSx}>
                {showAlerts && (
                  <TargetResultAlertNumber
                    expectationResult={expectationResult}
                    injectExpectationId={injectExpectation.inject_expectation_id}
                    onShowAlerts={() => onOpenAlertsDialog(expectationResult, injectExpectation)}
                  />
                )}
                {showAlertsDash && '-'}
              </Box>
              <Box
                onClick={e => e.stopPropagation()}
                sx={{
                  ...cellSx,
                  justifyContent: 'flex-end',
                }}
              >
                <ButtonPopover
                  disabled={['collector', 'media-pressure', 'challenge'].includes(expectationResult.sourceType ?? 'unknown')}
                  entries={[{
                    label: t('Update'),
                    action: () => onOpenEditInjectExpectationResultResult(expectationResult, injectExpectation),
                    disabled: false,
                    userRight: true,
                  },
                  {
                    label: t('Delete'),
                    action: () => onOpenDeleteInjectExpectationResult(expectationResult, injectExpectation),
                    disabled: false,
                    userRight: true,
                  }]}
                  variant="icon"
                />
              </Box>
            </Box>
          );
        })}
      </Box>
    </div>
  );
};
export default InjectExpectationResultList;

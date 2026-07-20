import { Box, Chip, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { Fragment, useContext } from 'react';

import ButtonPopover from '../../../../../components/common/ButtonPopover';
import { useFormatter } from '../../../../../components/i18n';
import {
  type Inject,
  type InjectExpectationOutput,
  type InjectExpectationResult,
  type PayloadSimple,
} from '../../../../../utils/api-types';
import { buildTenantApiPath } from '../../../../../utils/url-helper';
import { isNotEmptyField } from '../../../../../utils/utils';
import { type InjectExpectationsStore } from '../../../common/injects/expectations/Expectation';
import InjectIcon from '../../../common/injects/InjectIcon';
import InjectExpectationContext from '../context/InjectExpectationContext';
import StatusPill from './StatusPill';
import TargetResultAlertNumber from './TargetResultAlertNumber';

// Stable accent per security-platform category (kept in sync with the exposure
// console orbit colors), so each platform type reads as a distinct chip.
const PLATFORM_TYPE_COLORS: Record<string, string> = {
  EDR: '#0fbcff',
  XDR: '#00bcd4',
  SIEM: '#ffb300',
  SOAR: '#9575cd',
  NDR: '#26a96c',
  ISPM: '#ff7043',
  LLM_FIREWALL: '#00f1bd',
  AI_GATEWAY: '#7e57c2',
};

// "LLM_FIREWALL" -> "LLM firewall": first token upper (acronym), rest lower.
const humanizePlatformType = (type: string): string => type
  .split('_')
  .map((word, index) => (index === 0 ? word : word.toLowerCase()))
  .join(' ');

interface Props {
  injectExpectation: InjectExpectationsStore;
  injectExpectationResults: InjectExpectationResult[];
  injectExpectationAgent: InjectExpectationOutput['inject_expectation_agent'];
  injectorContractPayload?: PayloadSimple;
  injectType: Inject['inject_type'];
}

const GRID_TEMPLATE_COLUMNS = 'minmax(180px, 2fr) 150px 140px 160px 80px 40px';

const InjectExpectationResultList = ({
  injectExpectation,
  injectExpectationResults,
  injectExpectationAgent,
  injectorContractPayload,
  injectType,
}: Props) => {
  const { nsdt, t } = useFormatter();
  const theme = useTheme();

  const { onOpenDeleteInjectExpectationResult, onOpenEditInjectExpectationResultResult, onOpenSecurityPlatform } = useContext(InjectExpectationContext);

  const getAvatar = (expectationResult: InjectExpectationResult) => {
    if (expectationResult.sourceType === 'collector' || expectationResult.sourceType === 'security-platform') {
      return (
        <img
          src={expectationResult.sourceType === 'collector'
            ? buildTenantApiPath(`/api/collectors/id/${expectationResult.sourceId}/image`)
            : buildTenantApiPath(`/api/images/security_platforms/id/${expectationResult.sourceId}/${theme.palette.mode}`)}
          alt={expectationResult.sourceId}
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
      <Box
        sx={{
          display: 'grid',
          gridTemplateColumns: GRID_TEMPLATE_COLUMNS,
          alignItems: 'center',
          columnGap: 1,
          minWidth: 720,
        }}
      >
        <Typography sx={headerSx}>{t('Security platforms')}</Typography>
        <Typography sx={headerSx}>{t('Type')}</Typography>
        <Typography sx={headerSx}>{t('Status')}</Typography>
        <Typography sx={headerSx}>{t('Detection time')}</Typography>
        <Typography sx={headerSx}>{t('Alerts')}</Typography>
        <span aria-hidden />

        {injectExpectationResults.map((expectationResult, index) => {
          const isResultSecurityPlatform: boolean = !!(
            injectExpectationAgent
            && (expectationResult.result === 'Prevented' || expectationResult.result === 'Detected')
            && expectationResult.sourceType === 'collector'
          );
          const showDetectionTime = expectationResult.result === 'Prevented' || expectationResult.result === 'Detected' || expectationResult.result === 'SUCCESS';
          const showAlerts = !!(expectationResult.sourceId
            && injectExpectationAgent
            && expectationResult.sourceType === 'collector'
            && (expectationResult.result === 'Prevented' || expectationResult.result === 'Detected'));
          const showAlertsDash = (!injectExpectationAgent
            || (injectExpectationAgent && (expectationResult.result === 'Not Detected' || expectationResult.result === 'Not Prevented'))
            || (injectExpectationAgent && expectationResult.sourceType !== 'collector' && (expectationResult.result === 'Prevented' || expectationResult.result === 'Detected'))
          );

          const cellSx = {
            display: 'flex',
            alignItems: 'center',
            minHeight: 44,
            cursor: isResultSecurityPlatform ? 'pointer' : 'default',
            ...(isResultSecurityPlatform && { '&:hover': { backgroundColor: alpha(theme.palette.text.primary, 0.04) } }),
          } as const;

          const handleRowClick = () => {
            if (isResultSecurityPlatform) {
              onOpenSecurityPlatform(expectationResult, injectExpectation);
            }
          };

          const sourceName = expectationResult.sourceName?.trim() || '-';
          const platformType = expectationResult.sourcePlatform?.trim();
          const typeColor = platformType
            ? (PLATFORM_TYPE_COLORS[platformType.toUpperCase()] ?? theme.palette.primary.main)
            : theme.palette.primary.main;

          return (
            <Fragment key={`${expectationResult.sourceName}-${index}`}>
              {/* Full-width separator so the row divider is continuous across all
                  columns (a per-cell border breaks at each column gap). */}
              <Box
                aria-hidden
                sx={{
                  gridColumn: '1 / -1',
                  borderTop: `1px solid ${alpha(theme.palette.text.primary, 0.08)}`,
                }}
              />
              <Box sx={cellSx} onClick={handleRowClick}>
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
                </div>
              </Box>
              <Box sx={cellSx} onClick={handleRowClick}>
                {platformType
                  ? (
                      <Chip
                        label={humanizePlatformType(platformType)}
                        size="small"
                        variant="outlined"
                        sx={{
                          height: 22,
                          fontSize: 11,
                          fontWeight: 600,
                          borderRadius: 1,
                          color: typeColor,
                          borderColor: alpha(typeColor, 0.4),
                          backgroundColor: alpha(typeColor, 0.08),
                        }}
                      />
                    )
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
              <Box sx={cellSx} onClick={handleRowClick}>
                {expectationResult.result && (
                  <StatusPill
                    label={t(expectationResult.result)}
                    status={expectationResult.result}
                  />
                )}
              </Box>
              <Box sx={cellSx} onClick={handleRowClick}>
                <Typography sx={{
                  fontSize: 13,
                  fontVariantNumeric: 'tabular-nums',
                }}
                >
                  {showDetectionTime ? nsdt(expectationResult.date) : '-'}
                </Typography>
              </Box>
              <Box sx={cellSx} onClick={handleRowClick}>
                {showAlerts && (
                  <TargetResultAlertNumber expectationResult={expectationResult} injectExpectationId={injectExpectation.inject_expectation_id} />
                )}
                {showAlertsDash && '-'}
              </Box>
              <Box sx={{
                ...cellSx,
                cursor: 'default',
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
            </Fragment>
          );
        })}
      </Box>
    </div>
  );
};
export default InjectExpectationResultList;

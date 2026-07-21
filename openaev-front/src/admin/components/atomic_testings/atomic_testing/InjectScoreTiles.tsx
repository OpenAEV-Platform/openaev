import { Box, Tooltip, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../../components/i18n';
import { type ExpectationResultsByType } from '../../../../utils/api-types';
import { computeInjectExpectationLabel, getStatusColor } from '../../../../utils/statusUtils';
import { capitalize } from '../../../../utils/String';
import { expectationTypeIcon } from '../../common/ExpectationIconByType';
import { expectationResultTypes } from '../../common/injects/expectations/Expectation';

interface Props { expectationResultsByTypes: ExpectationResultsByType[] | null | undefined }

/**
 * Compact per-expectation-type score tiles (status label + stacked distribution
 * bar). Replaces the ResponsePie donuts in the inject / atomic testing heroes.
 */
const InjectScoreTiles: FunctionComponent<Props> = ({ expectationResultsByTypes }) => {
  const { t } = useFormatter();
  const theme = useTheme();

  const entries = (expectationResultsByTypes ?? [])
    .filter(entry => entry?.type)
    .toSorted((a, b) => expectationResultTypes.indexOf(a.type) - expectationResultTypes.indexOf(b.type));

  if (entries.length === 0) {
    return null;
  }

  return (
    <div
      id="score_details"
      style={{
        display: 'flex',
        flexWrap: 'wrap',
        gap: theme.spacing(1),
      }}
    >
      {entries.map((entry) => {
        const Icon = expectationTypeIcon(entry.type);
        const hasDistribution = (entry.distribution?.length ?? 0) > 0;
        const isUnknown = entry.avgResult === 'UNKNOWN' || !hasDistribution;
        // Same label derivation as the expectation cards (statusUtils):
        // SUCCESS + PREVENTION = "Prevented", PENDING = "Pending", ...
        // HUMAN_RESPONSE is not covered by the map, fall back to the raw status.
        const statusLabel = computeInjectExpectationLabel(entry.avgResult, entry.type) ?? capitalize(entry.avgResult.toLowerCase());
        const statusText = isUnknown
          ? t('No expectation for {type}', { type: t(entry.type) })
          : t(statusLabel);
        const statusColor = isUnknown
          ? theme.palette.text.disabled
          : getStatusColor(theme, statusLabel);
        const total = hasDistribution
          ? entry.distribution.reduce((sum, item) => sum + (item.value ?? 0), 0)
          : 0;

        return (
          <Box
            key={entry.type}
            sx={{
              minWidth: 170,
              padding: 1.25,
              borderRadius: 1,
              border: `1px solid ${alpha(theme.palette.text.primary, 0.08)}`,
              display: 'flex',
              flexDirection: 'column',
              gap: 1,
            }}
          >
            <div style={{
              display: 'flex',
              alignItems: 'center',
              gap: theme.spacing(1),
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
                  backgroundColor: alpha(statusColor, 0.12),
                }}
              >
                <Icon sx={{
                  fontSize: 18,
                  color: statusColor,
                }}
                />
              </Box>
              <div style={{ minWidth: 0 }}>
                <Typography
                  sx={{
                    fontSize: 11,
                    textTransform: 'uppercase',
                    letterSpacing: '0.08em',
                    color: 'text.secondary',
                    fontFamily: theme.typography.h1.fontFamily,
                    lineHeight: 1.4,
                    whiteSpace: 'nowrap',
                  }}
                >
                  {t(entry.type)}
                </Typography>
                <Typography
                  sx={{
                    fontSize: 13,
                    fontWeight: 600,
                    color: statusColor,
                    lineHeight: 1.3,
                    whiteSpace: 'nowrap',
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                  }}
                >
                  {statusText}
                </Typography>
              </div>
            </div>
            {hasDistribution && total > 0 ? (
              <div style={{
                display: 'flex',
                height: 4,
                borderRadius: theme.spacing(0.25),
                overflow: 'hidden',
                gap: 1,
              }}
              >
                {entry.distribution
                  .filter(item => (item.value ?? 0) > 0)
                  .map(item => (
                    <Tooltip key={item.id} title={`${t(item.label)} (${item.value})`}>
                      <div style={{
                        flexGrow: item.value ?? 0,
                        backgroundColor: getStatusColor(theme, item.label),
                      }}
                      />
                    </Tooltip>
                  ))}
              </div>
            ) : (
              <div
                aria-hidden
                style={{
                  height: 4,
                  borderRadius: theme.spacing(0.25),
                  backgroundColor: alpha(theme.palette.text.primary, 0.08),
                }}
              />
            )}
          </Box>
        );
      })}
    </div>
  );
};

export default InjectScoreTiles;

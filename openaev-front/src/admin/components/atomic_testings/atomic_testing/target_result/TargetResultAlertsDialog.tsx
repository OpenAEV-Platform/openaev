import { CloseOutlined, NotificationsActiveOutlined, NotificationsOffOutlined, OpenInNew } from '@mui/icons-material';
import { Box, Dialog, DialogContent, IconButton, Skeleton, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type FunctionComponent, useEffect, useState } from 'react';

import { fetchExpectationTraces } from '../../../../../actions/atomic_testings/atomic-testing-actions';
import Transition from '../../../../../components/common/Transition';
import { useFormatter } from '../../../../../components/i18n';
import ItemSecurityPlatformType from '../../../../../components/ItemSecurityPlatformType';
import type { InjectExpectationResult, InjectExpectationTrace } from '../../../../../utils/api-types';
import { type InjectExpectationsStore } from '../../../common/injects/expectations/Expectation';
import useExpectationSourceLogo from './useExpectationSourceLogo';

interface Props {
  injectExpectation: InjectExpectationsStore;
  sourceId: string;
  expectationResult: InjectExpectationResult | null;
  open: boolean;
  handleClose: () => void;
}

// Extracts a compact "security.microsoft.com"-style host from an alert link so
// each row can surface where the alert lives without showing the full URL.
const extractHost = (link?: string): string | undefined => {
  if (!link) {
    return undefined;
  }
  try {
    return new URL(link).host || undefined;
  } catch {
    return undefined;
  }
};

const TargetResultAlertsDialog: FunctionComponent<Props> = ({
  injectExpectation,
  sourceId,
  expectationResult,
  handleClose,
  open,
}) => {
  const { t, fldt } = useFormatter();
  const theme = useTheme();
  const [expectationTraces, setExpectationTraces] = useState<InjectExpectationTrace[]>([]);
  const [loading, setLoading] = useState<boolean>(true);

  useEffect(() => {
    setLoading(true);
    fetchExpectationTraces(injectExpectation.inject_expectation_id, sourceId)
      .then((result: { data: InjectExpectationTrace[] }) => setExpectationTraces(result.data ?? []))
      .finally(() => setLoading(false));
  }, [injectExpectation.inject_expectation_id, sourceId]);

  const sourceName = expectationResult?.sourceName?.trim() || '-';
  const platformType = expectationResult?.sourcePlatform?.trim();
  const expectationTypeLabel = injectExpectation.inject_expectation_type
    ? t(injectExpectation.inject_expectation_type)
    : t('Alerts');

  // Platform-first logo resolution: a result written by a since-deleted
  // collector still resolves to its (surviving) security platform logo.
  const { resolveLogoSrc, onLogoError } = useExpectationSourceLogo();
  const logoSrc = expectationResult ? resolveLogoSrc(expectationResult) : undefined;

  const sortedTraces = [...expectationTraces].sort((a, b) => {
    const dateA = a.inject_expectation_trace_date ?? a.inject_expectation_trace_created_at ?? '';
    const dateB = b.inject_expectation_trace_date ?? b.inject_expectation_trace_created_at ?? '';
    return dateB.localeCompare(dateA);
  });

  const alertCountLabel = (() => {
    if (loading) {
      return null;
    }
    if (sortedTraces.length === 1) {
      return `1 ${t('alert')}`;
    }
    return `${sortedTraces.length} ${t('alerts')}`;
  })();

  return (
    <Dialog
      open={open}
      onClose={handleClose}
      fullWidth
      maxWidth="sm"
      TransitionComponent={Transition}
      slotProps={{ paper: { elevation: 1 } }}
      data-testid="target-result-alerts-dialog"
    >
      {/* Header: framed platform logo + name + type context + close */}
      <Box sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 1.5,
        padding: theme.spacing(2, 2, 1.5, 2.5),
      }}
      >
        <Box
          aria-hidden
          sx={{
            width: 44,
            height: 44,
            flexShrink: 0,
            borderRadius: 1,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            backgroundColor: alpha(theme.palette.text.primary, 0.04),
            border: `1px solid ${alpha(theme.palette.text.primary, 0.08)}`,
          }}
        >
          {logoSrc
            ? (
                <img
                  src={logoSrc}
                  alt={sourceName}
                  onError={onLogoError}
                  style={{
                    width: 26,
                    height: 26,
                    borderRadius: 4,
                  }}
                />
              )
            : <NotificationsActiveOutlined sx={{ color: 'text.secondary' }} />}
        </Box>
        <div style={{
          minWidth: 0,
          flex: 1,
        }}
        >
          <Typography
            sx={{
              fontFamily: theme.typography.h1.fontFamily,
              fontSize: 16,
              fontWeight: 600,
              lineHeight: 1.3,
              whiteSpace: 'nowrap',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
            }}
          >
            {sourceName}
          </Typography>
          <Box sx={{
            display: 'flex',
            alignItems: 'center',
            gap: 1,
            marginTop: 0.5,
            minWidth: 0,
          }}
          >
            {platformType && <ItemSecurityPlatformType type={platformType} />}
            <Typography sx={{
              fontSize: 12,
              color: 'text.secondary',
              whiteSpace: 'nowrap',
            }}
            >
              {alertCountLabel ? `${expectationTypeLabel} · ${alertCountLabel}` : expectationTypeLabel}
            </Typography>
          </Box>
        </div>
        <IconButton
          aria-label={t('Close')}
          size="small"
          onClick={handleClose}
          sx={{ alignSelf: 'flex-start' }}
        >
          <CloseOutlined fontSize="small" />
        </IconButton>
      </Box>
      <DialogContent sx={{
        padding: theme.spacing(0.5, 1.5, 2),
        borderTop: `1px solid ${alpha(theme.palette.text.primary, 0.08)}`,
      }}
      >
        {loading && (
          <Box sx={{
            display: 'flex',
            flexDirection: 'column',
            gap: 1,
            paddingTop: 1.5,
          }}
          >
            {[0, 1, 2].map(skeletonIndex => (
              <Box
                key={skeletonIndex}
                sx={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 1.5,
                  paddingInline: 1,
                }}
              >
                <Skeleton variant="rounded" width={32} height={32} />
                <div style={{ flex: 1 }}>
                  <Skeleton variant="text" width="70%" />
                  <Skeleton variant="text" width="40%" />
                </div>
              </Box>
            ))}
          </Box>
        )}
        {!loading && sortedTraces.length === 0 && (
          <Box sx={{
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            gap: 1,
            paddingBlock: 5,
          }}
          >
            <NotificationsOffOutlined sx={{
              fontSize: 32,
              color: 'text.disabled',
            }}
            />
            <Typography sx={{
              fontSize: 13,
              color: 'text.secondary',
              textAlign: 'center',
            }}
            >
              {t('No alerts have been reported by this security platform.')}
            </Typography>
          </Box>
        )}
        {!loading && sortedTraces.length > 0 && (
          <Box sx={{
            display: 'flex',
            flexDirection: 'column',
            paddingTop: 0.5,
          }}
          >
            {sortedTraces.map((expectationTrace, traceIndex) => {
              const alertName = expectationTrace.inject_expectation_trace_alert_name?.trim() || t('Unknown');
              const alertLink = expectationTrace.inject_expectation_trace_alert_link;
              const alertHost = extractHost(alertLink);
              const alertDate = expectationTrace.inject_expectation_trace_date ?? expectationTrace.inject_expectation_trace_created_at;

              const rowContent = (
                <>
                  <Box
                    aria-hidden
                    sx={{
                      width: 32,
                      height: 32,
                      flexShrink: 0,
                      borderRadius: 1,
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      backgroundColor: alpha(theme.palette.primary.main, 0.08),
                    }}
                  >
                    <NotificationsActiveOutlined sx={{
                      fontSize: 17,
                      color: 'primary.main',
                    }}
                    />
                  </Box>
                  <div style={{
                    minWidth: 0,
                    flex: 1,
                  }}
                  >
                    <Typography sx={{
                      fontSize: 13,
                      fontWeight: 600,
                      lineHeight: 1.35,
                      color: 'text.primary',
                      display: '-webkit-box',
                      WebkitLineClamp: 2,
                      WebkitBoxOrient: 'vertical',
                      overflow: 'hidden',
                      wordBreak: 'break-word',
                    }}
                    >
                      {alertName}
                    </Typography>
                    <Typography sx={{
                      fontSize: 11.5,
                      color: 'text.secondary',
                      marginTop: 0.25,
                      whiteSpace: 'nowrap',
                      overflow: 'hidden',
                      textOverflow: 'ellipsis',
                      fontVariantNumeric: 'tabular-nums',
                    }}
                    >
                      {[alertHost, alertDate ? fldt(alertDate) : undefined].filter(Boolean).join(' · ')}
                    </Typography>
                  </div>
                  {alertLink && (
                    <OpenInNew sx={{
                      fontSize: 16,
                      flexShrink: 0,
                      color: 'text.secondary',
                    }}
                    />
                  )}
                </>
              );

              const rowSx = {
                display: 'flex',
                alignItems: 'center',
                gap: 1.5,
                paddingInline: 1,
                paddingBlock: 1.25,
                borderRadius: 1,
                ...(traceIndex > 0 && { borderTop: `1px solid ${alpha(theme.palette.text.primary, 0.05)}` }),
              } as const;

              return alertLink
                ? (
                    <Box
                      key={expectationTrace.inject_expectation_trace_id}
                      component="a"
                      href={alertLink}
                      target="_blank"
                      rel="noopener noreferrer"
                      data-testid="target-result-alert-row"
                      sx={{
                        ...rowSx,
                        'textDecoration': 'none',
                        'cursor': 'pointer',
                        '&:hover': {
                          'backgroundColor': theme.palette.action.hover,
                          '& .MuiSvgIcon-root': { color: 'primary.main' },
                        },
                      }}
                    >
                      {rowContent}
                    </Box>
                  )
                : (
                    <Box
                      key={expectationTrace.inject_expectation_trace_id}
                      data-testid="target-result-alert-row"
                      sx={rowSx}
                    >
                      {rowContent}
                    </Box>
                  );
            })}
          </Box>
        )}
      </DialogContent>
    </Dialog>
  );
};

export default TargetResultAlertsDialog;

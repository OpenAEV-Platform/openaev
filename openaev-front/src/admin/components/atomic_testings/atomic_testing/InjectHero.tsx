import { EventAvailableOutlined, LabelOutlined, RouteOutlined, ScheduleOutlined, TimerOutlined } from '@mui/icons-material';
import { Box, Tooltip, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type FunctionComponent, type ReactNode } from 'react';

import { useFormatter } from '../../../../components/i18n';
import PlatformIcon from '../../../../components/PlatformIcon';
import type { InjectResultOverviewOutput } from '../../../../utils/api-types';
import InjectIcon from '../../common/injects/InjectIcon';
import AtomicTestingTitle from './AtomicTestingTitle';
import InjectScoreTiles from './InjectScoreTiles';

interface Props {
  injectResultOverview: InjectResultOverviewOutput;
  actions?: ReactNode;
}

// A single compact meta item (icon + inline content) for the hero metadata row.
const MetaItem = ({ icon, children }: {
  icon: ReactNode;
  children: ReactNode;
}) => (
  <span style={{
    display: 'inline-flex',
    alignItems: 'center',
    gap: 6,
    minWidth: 0,
  }}
  >
    {icon}
    <span style={{
      display: 'inline-flex',
      alignItems: 'center',
      gap: 6,
      minWidth: 0,
    }}
    >
      {children}
    </span>
  </span>
);

/**
 * Marketplace-style hero header shared by the atomic testing page and the
 * simulation inject detail page (breadcrumbs and tabs live outside of it).
 */
const InjectHero: FunctionComponent<Props> = ({ injectResultOverview, actions }) => {
  const theme = useTheme();
  const { t, tPick, nsdt, du } = useFormatter();

  const payload = injectResultOverview.inject_injector_contract?.injector_contract_payload;
  const iconType = payload
    ? payload.payload_collector_type ?? payload.payload_type
    : injectResultOverview.inject_type;
  const contractLabel = tPick(injectResultOverview.inject_injector_contract?.injector_contract_labels)
    || injectResultOverview.inject_type
    || '';

  // Relevant-at-a-glance metadata; each item is rendered only when it has a value
  // so the hero stays light (no empty "-" rows like the old info tooltip).
  // The execution window (start / end / duration) now lives here since the
  // dedicated "Execution details" tab was dropped.
  const startDate = injectResultOverview.inject_status?.tracking_sent_date;
  const endDate = injectResultOverview.inject_status?.tracking_end_date;
  const duration = startDate && endDate
    ? du(new Date(endDate).getTime() - new Date(startDate).getTime())
    : null;
  const platforms = injectResultOverview.inject_injector_contract?.injector_contract_platforms ?? [];
  const killChainPhases = injectResultOverview.inject_kill_chain_phases ?? [];
  const metaIconSx = {
    fontSize: 15,
    color: 'text.disabled',
  } as const;
  const metaTextSx = {
    fontSize: 12.5,
    color: 'text.secondary',
  } as const;
  const hasMeta = !!startDate || !!endDate || platforms.length > 0 || killChainPhases.length > 0;

  return (
    <Box
      component="section"
      sx={{
        position: 'relative',
        overflow: 'hidden',
        borderRadius: 1,
        border: `1px solid ${alpha(theme.palette.text.primary, 0.08)}`,
        backgroundColor: theme.palette.background.paper,
        padding: 3,
      }}
    >
      {/* Decorative glow, purely visual. */}
      <Box
        aria-hidden
        sx={{
          pointerEvents: 'none',
          position: 'absolute',
          top: -100,
          right: -60,
          width: 260,
          height: 260,
          borderRadius: '50%',
          background: alpha(theme.palette.primary.main, 0.08),
          filter: 'blur(60px)',
        }}
      />
      <div style={{
        position: 'relative',
        display: 'flex',
        alignItems: 'flex-start',
        justifyContent: 'space-between',
        gap: theme.spacing(2),
        flexWrap: 'wrap',
      }}
      >
        <div style={{
          flex: 1,
          minWidth: 0,
        }}
        >
          <div style={{
            display: 'flex',
            alignItems: 'center',
            gap: theme.spacing(1.5),
            minWidth: 0,
          }}
          >
            <Box
              sx={{
                flexShrink: 0,
                width: 56,
                height: 56,
                borderRadius: 1,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                border: `1px solid ${alpha(theme.palette.text.primary, 0.1)}`,
                backgroundColor: alpha(theme.palette.text.primary, 0.04),
              }}
            >
              <InjectIcon
                type={iconType}
                isPayload={!!payload}
                variant="list"
              />
            </Box>
            <div style={{ minWidth: 0 }}>
              {contractLabel && (
                <Typography
                  sx={{
                    fontSize: 12,
                    textTransform: 'uppercase',
                    letterSpacing: '0.06em',
                    color: 'primary.main',
                    fontFamily: theme.typography.h1.fontFamily,
                    lineHeight: 1.4,
                    whiteSpace: 'nowrap',
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                  }}
                >
                  {contractLabel}
                </Typography>
              )}
              <AtomicTestingTitle injectResultOverview={injectResultOverview} />
            </div>
          </div>
          {hasMeta && (
            <Box sx={{
              marginTop: 1.5,
              display: 'flex',
              alignItems: 'center',
              flexWrap: 'wrap',
              columnGap: 2.5,
              rowGap: 1,
            }}
            >
              {startDate && (
                <MetaItem icon={<ScheduleOutlined sx={metaIconSx} />}>
                  <Typography component="span" sx={metaTextSx}>
                    {t('Start date')}
                    {': '}
                    {nsdt(startDate)}
                  </Typography>
                </MetaItem>
              )}
              {endDate && (
                <MetaItem icon={<EventAvailableOutlined sx={metaIconSx} />}>
                  <Typography component="span" sx={metaTextSx}>
                    {t('End date')}
                    {': '}
                    {nsdt(endDate)}
                  </Typography>
                </MetaItem>
              )}
              {duration && (
                <MetaItem icon={<TimerOutlined sx={metaIconSx} />}>
                  <Typography component="span" sx={metaTextSx}>
                    {t('Duration')}
                    {': '}
                    {duration}
                  </Typography>
                </MetaItem>
              )}
              {platforms.length > 0 && (
                <MetaItem icon={null}>
                  {platforms.map(platform => (
                    <Tooltip key={platform} title={platform}>
                      <span style={{ display: 'inline-flex' }}>
                        <PlatformIcon platform={platform} width={16} />
                      </span>
                    </Tooltip>
                  ))}
                </MetaItem>
              )}
              {killChainPhases.length > 0 && (
                <MetaItem icon={<RouteOutlined sx={metaIconSx} />}>
                  <Typography component="span" sx={metaTextSx}>
                    {killChainPhases.map(phase => phase.phase_name).join(', ')}
                  </Typography>
                </MetaItem>
              )}
              {(injectResultOverview.injects_tags?.length ?? 0) > 0 && (
                <MetaItem icon={<LabelOutlined sx={metaIconSx} />}>
                  <Typography component="span" sx={metaTextSx}>
                    {injectResultOverview.injects_tags?.length}
                    {' '}
                    {t('tag(s)')}
                  </Typography>
                </MetaItem>
              )}
            </Box>
          )}
          <Box sx={{ marginTop: 2 }}>
            <InjectScoreTiles expectationResultsByTypes={injectResultOverview.inject_expectation_results} />
          </Box>
        </div>
        {actions && (
          <div style={{ flexShrink: 0 }}>
            {actions}
          </div>
        )}
      </div>
    </Box>
  );
};

export default InjectHero;

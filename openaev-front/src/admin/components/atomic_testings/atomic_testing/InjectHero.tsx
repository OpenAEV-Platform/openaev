import { EventAvailableOutlined, LabelOutlined, RouteOutlined, ScheduleOutlined, TimerOutlined } from '@mui/icons-material';
import { Box, Tooltip, Typography } from '@mui/material';
import { type FunctionComponent, type ReactNode } from 'react';

import { DetailHero } from '../../../../components/common/detail/EntityDetailCommon';
import { useFormatter } from '../../../../components/i18n';
import Loader from '../../../../components/Loader';
import PlatformIcon from '../../../../components/PlatformIcon';
import type { InjectResultOverviewOutput, InjectStatus as InjectStatusType } from '../../../../utils/api-types';
import { truncate } from '../../../../utils/String';
import InjectIcon from '../../common/injects/InjectIcon';
import InjectStatus from '../../common/injects/status/InjectStatus';
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
 * Hero header shared by the atomic testing page and the simulation inject
 * detail page (breadcrumbs and tabs live outside of it). Built on the shared
 * DetailHero so the geometry, icon box and action sizing match every other
 * entity detail page; the inject-specific metadata row and the score tiles
 * render in the hero footer.
 */
const InjectHero: FunctionComponent<Props> = ({ injectResultOverview, actions }) => {
  const { t, tPick, nsdt, du } = useFormatter();

  if (!injectResultOverview) {
    return <Loader variant="inElement" />;
  }

  const payload = injectResultOverview.inject_injector_contract?.injector_contract_payload;
  const iconType = payload
    ? payload.payload_collector_type ?? payload.payload_type
    : injectResultOverview.inject_type;
  const contractLabel = tPick(injectResultOverview.inject_injector_contract?.injector_contract_labels)
    || injectResultOverview.inject_type
    || '';

  // Relevant-at-a-glance metadata; each item is rendered only when it has a value
  // so the hero stays light (no empty "-" rows like the old info tooltip).
  // The execution window (start / end / duration) lives here since the
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
    <DetailHero
      iconNode={(
        <InjectIcon
          type={iconType}
          isPayload={!!payload}
          variant="list"
        />
      )}
      overline={contractLabel || undefined}
      title={truncate(injectResultOverview.inject_title, 80) ?? ''}
      chips={<InjectStatus status={injectResultOverview.inject_status?.status_name as InjectStatusType['status_name']} />}
      action={actions}
      footer={(
        <>
          {hasMeta && (
            <Box sx={{
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
          <InjectScoreTiles expectationResultsByTypes={injectResultOverview.inject_expectation_results} />
        </>
      )}
    />
  );
};

export default InjectHero;

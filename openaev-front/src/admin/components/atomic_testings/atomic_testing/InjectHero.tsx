import { EventAvailableOutlined, LabelOutlined, RouteOutlined, ScheduleOutlined, TimerOutlined } from '@mui/icons-material';
import { alpha, Box, Chip, Tooltip, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, type ReactNode, useEffect, useMemo, useState } from 'react';

import { getInjectStatusWithGlobalExecutionTraces } from '../../../../actions/injects/inject-action';
import { DetailHero } from '../../../../components/common/detail/EntityDetailCommon';
import { useFormatter } from '../../../../components/i18n';
import PlatformIcon from '../../../../components/PlatformIcon';
import type { InjectResultOverviewOutput, InjectStatus as InjectStatusType, InjectStatusOutput } from '../../../../utils/api-types';
import handle from '../../../../utils/period/Period';
import { truncate } from '../../../../utils/String';
import InjectIcon from '../../common/injects/InjectIcon';
import InjectStatus from '../../common/injects/status/InjectStatus';
import PayloadDeprecatedChip from '../../payloads/PayloadDeprecatedChip';
import InjectScoreTiles from './InjectScoreTiles';

// Inject-level statuses whose concrete failure reason is worth surfacing on the
// status chip tooltip (fetched from the global execution traces).
const INJECT_ERROR_STATUSES = ['ERROR', 'PARTIAL'];

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
  const { t, tPick, nsdt, du, locale, fld } = useFormatter();
  const theme = useTheme();

  const statusName = injectResultOverview.inject_status?.status_name;
  const [errorMessage, setErrorMessage] = useState<string | undefined>(undefined);

  // Recurring scheduling chip (atomic testings only): mirrors the scenario
  // hero Scheduled chip with the human-readable schedule as tooltip.
  const isScheduled = !!injectResultOverview.inject_recurrence;
  const scheduleLabel = useMemo(() => {
    const cronObject = handle(injectResultOverview.inject_recurrence);
    if (!cronObject?.isValid()) {
      return null;
    }
    let sentence = cronObject.toTranslatableStringArray(locale).map(element => t(element)).join(' ');
    // Open-ended schedules are legal (null start fires immediately, null end never expires):
    // omit the fragment instead of rendering "from None" via fld(undefined).
    if (injectResultOverview.inject_recurrence_start) {
      sentence += ` ${t(injectResultOverview.inject_recurrence_end ? 'recurrence_from' : 'recurrence_starting_from')} ${fld(injectResultOverview.inject_recurrence_start)}`;
    }
    if (injectResultOverview.inject_recurrence_end) {
      sentence += ` ${t('recurrence_to')} ${fld(injectResultOverview.inject_recurrence_end)}`;
    }
    return sentence;
  }, [injectResultOverview.inject_recurrence, injectResultOverview.inject_recurrence_start, injectResultOverview.inject_recurrence_end, locale]);

  // Surface the concrete failure reason on the status chip: the global execution
  // traces hold the real error (e.g. unmet dependencies), while the chip would
  // otherwise only show a generic "could not be completed" tooltip.
  useEffect(() => {
    setErrorMessage(undefined);
    if (!statusName || !INJECT_ERROR_STATUSES.includes(statusName)) {
      return;
    }
    getInjectStatusWithGlobalExecutionTraces(injectResultOverview.inject_id)
      .then((response: { data: InjectStatusOutput }) => {
        const firstError = (response.data?.status_main_traces ?? [])
          .find(trace => trace.execution_message?.trim());
        setErrorMessage(firstError?.execution_message);
      })
      .catch(() => setErrorMessage(undefined));
  }, [injectResultOverview.inject_id, statusName]);

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
      chips={(
        <>
          <InjectStatus status={statusName as InjectStatusType['status_name']} errorMessage={errorMessage} />
          <PayloadDeprecatedChip status={payload?.payload_status} />
          {isScheduled && (
            <Tooltip title={scheduleLabel ?? ''}>
              <Chip
                size="small"
                variant="outlined"
                label={t('Scheduled')}
                sx={{
                  borderRadius: 1,
                  height: 22,
                  fontSize: 11,
                  color: theme.palette.success.main,
                  borderColor: alpha(theme.palette.success.main, 0.4),
                }}
              />
            </Tooltip>
          )}
        </>
      )}
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

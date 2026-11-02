import { Chip, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { LabelOutlined, RouteOutlined, ScheduleOutlined } from '@mui/icons-material';
import { type FunctionComponent, type ReactNode, useEffect, useMemo, useState } from 'react';

import { getInjectStatusWithGlobalExecutionTraces } from '../../../../actions/injects/inject-action';
import { DetailHero, HeroMetaItem } from '../../../../components/common/detail/EntityDetailCommon';
import { useFormatter } from '../../../../components/i18n';
import PlatformIcon from '../../../../components/PlatformIcon';
import type { InjectResultOverviewOutput, InjectStatus as InjectStatusType, InjectStatusOutput } from '../../../../utils/api-types';
import handle from '../../../../utils/period/Period';
import { truncate } from '../../../../utils/String';
import InjectIcon from '../../common/injects/InjectIcon';
import InjectStatus from '../../common/injects/status/InjectStatus';
import { isLaunchBlocked, launchBlockedLabel } from '../../payloads/payloadApprovalDisplay';
import PayloadApprovalWarningChip from '../../payloads/PayloadApprovalWarningChip';
import PayloadDeprecatedChip from '../../payloads/PayloadDeprecatedChip';
import InjectScoreTiles from './InjectScoreTiles';

// Inject-level statuses whose concrete failure reason is worth surfacing on the
// status chip tooltip (fetched from the global execution traces).
const INJECT_ERROR_STATUSES = ['ERROR', 'PARTIAL'];

interface Props {
  injectResultOverview: InjectResultOverviewOutput;
  actions?: ReactNode;
}

/**
 * Hero header shared by the atomic testing page and the simulation inject
 * detail page (breadcrumbs and tabs live outside of it). Built on the shared
 * DetailHero so the geometry, icon box and action sizing match every other
 * entity detail page; the inject-specific metadata row and the score tiles
 * render in the hero footer.
 */
const InjectHero: FunctionComponent<Props> = ({ injectResultOverview, actions }) => {
  const { t, tPick, nsdt, du, locale, fld } = useFormatter();

  const statusName = injectResultOverview.inject_status?.status_name;
  const [errorMessage, setErrorMessage] = useState<string | undefined>(undefined);

  // Recurring scheduling chip (atomic testings only): mirrors the scenario
  // hero Scheduled chip with the human-readable schedule as tooltip.
  const isScheduled = !!injectResultOverview.inject_recurrence;
  // Paused: saved when its action became blocked or after a sensitive change; it stays paused until
  // the user saves or removes the schedule (no automatic resume).
  const launchBlockers = injectResultOverview.inject_launch_blocked_by;
  const isSchedulePaused = isScheduled && (!!injectResultOverview.inject_recurrence_paused_at || isLaunchBlocked(launchBlockers));
  const schedulePausedText = isLaunchBlocked(launchBlockers)
    ? `${launchBlockedLabel(t, launchBlockers)}. ${t('The schedule stays paused until you save it again, once the actions are approved.')}`
    : t('Schedule paused after a change to this atomic testing. Save the schedule again to resume.');
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
  const metaIcon = { fontSize: 16 };
  // When it ran, in one item: start and duration, exact start and end on hover.
  const runHint = [
    startDate && `${t('Start date')}: ${nsdt(startDate)}`,
    endDate && `${t('End date')}: ${nsdt(endDate)}`,
    duration && `${t('Duration')}: ${duration}`,
  ].filter(Boolean).join(' · ');
  const hasMeta = !!startDate || platforms.length > 0 || killChainPhases.length > 0 || (injectResultOverview.injects_tags?.length ?? 0) > 0;

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
          {/* Blocked by an action that is not approved: back to Draft until relaunched on purpose.
              Computed on read; the last run's results stay visible below. */}
          {isLaunchBlocked(injectResultOverview.inject_launch_blocked_by)
            ? <InjectStatus status="DRAFT" />
            : <InjectStatus status={statusName as InjectStatusType['status_name']} errorMessage={errorMessage} />}
          <PayloadDeprecatedChip status={payload?.payload_status} />
          <PayloadApprovalWarningChip
            approvalStatus={payload?.payload_approval_status}
            pendingVersion={payload?.payload_pending_version}
            payloadName={contractLabel || undefined}
          />
          {isScheduled && !isSchedulePaused && (
            <Tooltip>
              <TooltipTrigger asChild>
                <Chip label={t('Scheduled')} severity="low" />
              </TooltipTrigger>
              {(scheduleLabel ?? '') && <TooltipContent>{scheduleLabel ?? ''}</TooltipContent>}
            </Tooltip>
          )}
          {isSchedulePaused && (
            <Tooltip>
              <TooltipTrigger asChild>
                <Chip label={t('Paused')} severity="medium" />
              </TooltipTrigger>
              <TooltipContent>{schedulePausedText}</TooltipContent>
            </Tooltip>
          )}
        </>
      )}
      meta={hasMeta && (
        <>
          {startDate && (
            <HeroMetaItem
              icon={<ScheduleOutlined sx={metaIcon} />}
              label={duration ? `${nsdt(startDate)} · ${duration}` : nsdt(startDate)}
              hint={runHint}
            />
          )}
          {platforms.length > 0 && (
            <HeroMetaItem
              label={(
                <span style={{
                  display: 'inline-flex',
                  gap: 6,
                }}
                >
                  {platforms.map(platform => <PlatformIcon key={platform} platform={platform} width={16} />)}
                </span>
              )}
              hint={`${t('Platforms')}: ${platforms.join(', ')}`}
            />
          )}
          {killChainPhases.length > 0 && (
            <HeroMetaItem
              icon={<RouteOutlined sx={metaIcon} />}
              label={killChainPhases.map(phase => phase.phase_name).join(', ')}
              hint={`${t('Kill chain phases')}: ${killChainPhases.map(phase => phase.phase_name).join(', ')}`}
            />
          )}
          {(injectResultOverview.injects_tags?.length ?? 0) > 0 && (
            <HeroMetaItem
              icon={<LabelOutlined sx={metaIcon} />}
              label={`${injectResultOverview.injects_tags?.length} ${t('tag(s)')}`}
            />
          )}
        </>
      )}
      action={actions}
      // The body keeps the results only (facts about the run are in meta); no tiles, no body.
      footer={(injectResultOverview.inject_expectation_results ?? []).some(entry => entry?.type)
        ? <InjectScoreTiles expectationResultsByTypes={injectResultOverview.inject_expectation_results} />
        : undefined}
    />
  );
};

export default InjectHero;

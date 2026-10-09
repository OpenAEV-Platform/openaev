import { Chip, type ChipSeverity } from '@filigran/design-system';
import { type FunctionComponent } from 'react';
import { makeStyles } from 'tss-react/mui';

import { useFormatter } from '../../../../components/i18n';
import { type NotificationTriggerOutput } from '../../../../utils/api-types';
import { eventTypeLabel, resourceTypeLabel } from './triggerUtils';

// Design system list chips: square corners, 20px height, uppercase, alpha-tinted background
// (same pattern as ItemSeverity / ItemStatus)
const useStyles = makeStyles()(() => ({
  chipInList: {
    fontSize: 12,
    height: 20,
    borderRadius: 4,
    textTransform: 'uppercase',
    width: 100,
  },
  chipAuto: {
    fontSize: 12,
    height: 20,
    borderRadius: 4,
    textTransform: 'uppercase',
  },
  eventsContainer: {
    display: 'flex',
    flexWrap: 'wrap',
    gap: 4,
  },
}));

const eventTypeSeverity = (eventType: string): ChipSeverity => {
  switch (eventType) {
    case 'CREATE':
      return 'low';
    case 'UPDATE':
      return 'info';
    case 'DELETE':
      return 'critical';
    case 'SCORE_DEGRADATION':
      return 'medium';
    default:
      return 'neutral';
  }
};

// -- TYPE --

export const TriggerTypeChip: FunctionComponent<{ type?: NotificationTriggerOutput['notification_trigger_type'] }> = ({ type }) => {
  const { t } = useFormatter();
  const isDigest = type === 'DIGEST';
  return (
    <Chip label={isDigest ? t('Digest') : t('Live')} severity="info" />
  );
};

// -- RESOURCE TYPE --

export const TriggerResourceChip: FunctionComponent<{ trigger: NotificationTriggerOutput }> = ({ trigger }) => {
  const { t } = useFormatter();
  const label = trigger.notification_trigger_type === 'DIGEST'
    ? `${trigger.notification_trigger_children?.length ?? 0} ${t('trigger(s)')}`
    : t(resourceTypeLabel(trigger.notification_trigger_resource_type));
  return (
    <Chip label={label} severity="neutral" />
  );
};

// -- EVENTS --

export const TriggerEventChips: FunctionComponent<{ trigger: NotificationTriggerOutput }> = ({ trigger }) => {
  const { t } = useFormatter();
  const { classes } = useStyles();
  if (trigger.notification_trigger_type === 'DIGEST') {
    return (
      <Chip label={t(trigger.notification_trigger_period?.toLowerCase() ?? '-')} severity="info" />
    );
  }
  return (
    <div className={classes.eventsContainer}>
      {(trigger.notification_trigger_event_types ?? []).map(eventType => (
        <Chip key={eventType} severity={eventTypeSeverity(eventType)} label={t(eventTypeLabel(eventType))} />
      ))}
    </div>
  );
};

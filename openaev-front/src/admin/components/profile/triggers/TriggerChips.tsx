import { Chip } from '@mui/material';
import { type CSSProperties, type FunctionComponent } from 'react';
import { makeStyles } from 'tss-react/mui';

import colorStyles from '../../../../components/Color';
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

const eventTypeStyle = (eventType: string): CSSProperties => {
  switch (eventType) {
    case 'CREATE':
      return colorStyles.green;
    case 'UPDATE':
      return colorStyles.blue;
    case 'DELETE':
      return colorStyles.red;
    case 'SCORE_DEGRADATION':
      return colorStyles.orange;
    default:
      return colorStyles.grey;
  }
};

// -- TYPE --

export const TriggerTypeChip: FunctionComponent<{ type?: NotificationTriggerOutput['notification_trigger_type'] }> = ({ type }) => {
  const { t } = useFormatter();
  const { classes } = useStyles();
  const isDigest = type === 'DIGEST';
  return (
    <Chip
      classes={{ root: classes.chipInList }}
      style={isDigest ? colorStyles.lightPurple : colorStyles.blue}
      label={isDigest ? t('Digest') : t('Live')}
    />
  );
};

// -- RESOURCE TYPE --

export const TriggerResourceChip: FunctionComponent<{ trigger: NotificationTriggerOutput }> = ({ trigger }) => {
  const { t } = useFormatter();
  const { classes } = useStyles();
  const label = trigger.notification_trigger_type === 'DIGEST'
    ? `${trigger.notification_trigger_children?.length ?? 0} ${t('trigger(s)')}`
    : t(resourceTypeLabel(trigger.notification_trigger_resource_type));
  return (
    <Chip
      classes={{ root: classes.chipInList }}
      style={colorStyles.grey}
      label={label}
    />
  );
};

// -- EVENTS --

export const TriggerEventChips: FunctionComponent<{ trigger: NotificationTriggerOutput }> = ({ trigger }) => {
  const { t } = useFormatter();
  const { classes } = useStyles();
  if (trigger.notification_trigger_type === 'DIGEST') {
    return (
      <Chip
        classes={{ root: classes.chipAuto }}
        style={colorStyles.lightPurple}
        label={t(trigger.notification_trigger_period?.toLowerCase() ?? '-')}
      />
    );
  }
  return (
    <div className={classes.eventsContainer}>
      {(trigger.notification_trigger_event_types ?? []).map(eventType => (
        <Chip
          key={eventType}
          classes={{ root: classes.chipAuto }}
          style={eventTypeStyle(eventType)}
          label={t(eventTypeLabel(eventType))}
        />
      ))}
    </div>
  );
};

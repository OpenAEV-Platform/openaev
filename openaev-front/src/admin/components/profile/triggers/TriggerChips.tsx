import { Chip } from '@mui/material';
import { type CSSProperties, type FunctionComponent } from 'react';
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

const inlineStyles: Record<string, CSSProperties> = {
  blue: {
    backgroundColor: 'rgba(92, 123, 245, 0.08)',
    color: '#5c7bf5',
  },
  purple: {
    backgroundColor: 'rgba(171, 71, 188, 0.08)',
    color: '#ab47bc',
  },
  green: {
    backgroundColor: 'rgba(76, 175, 80, 0.08)',
    color: '#4caf50',
  },
  red: {
    backgroundColor: 'rgba(244, 67, 54, 0.08)',
    color: '#f44336',
  },
  orange: {
    backgroundColor: 'rgba(255, 152, 0, 0.08)',
    color: '#ff9800',
  },
  blueGrey: {
    backgroundColor: 'rgba(96, 125, 139, 0.08)',
    color: '#607d8b',
  },
};

const eventTypeStyle = (eventType: string): CSSProperties => {
  switch (eventType) {
    case 'CREATE':
      return inlineStyles.green;
    case 'UPDATE':
      return inlineStyles.blue;
    case 'DELETE':
      return inlineStyles.red;
    case 'SCORE_DEGRADATION':
      return inlineStyles.orange;
    default:
      return inlineStyles.blueGrey;
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
      style={isDigest ? inlineStyles.purple : inlineStyles.blue}
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
      style={inlineStyles.blueGrey}
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
        style={inlineStyles.purple}
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

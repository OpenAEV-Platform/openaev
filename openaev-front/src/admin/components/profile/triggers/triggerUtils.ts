import { type NotificationTriggerInput } from '../../../../utils/api-types';

export type TriggerEventType = NonNullable<NotificationTriggerInput['notification_trigger_event_types']>[number];

const LIFECYCLE_EVENT_TYPES: TriggerEventType[] = ['CREATE', 'UPDATE', 'DELETE'];

/** Curated resource types watchable by notification triggers, with the events each one sends (mirrors backend catalog). */
export interface TriggerResourceTypeOption {
  value: string;
  entityPrefix?: string;
  label: string;
  eventTypes: TriggerEventType[];
}

export const TRIGGER_RESOURCE_TYPES: TriggerResourceTypeOption[] = [
  {
    value: 'SCENARIO',
    entityPrefix: 'scenario',
    label: 'Scenario',
    eventTypes: [...LIFECYCLE_EVENT_TYPES, 'SCORE_DEGRADATION'],
  },
  {
    value: 'SIMULATION',
    entityPrefix: 'exercise',
    label: 'Simulation',
    eventTypes: LIFECYCLE_EVENT_TYPES,
  },
  {
    value: 'INJECT',
    entityPrefix: 'inject',
    label: 'Inject',
    eventTypes: LIFECYCLE_EVENT_TYPES,
  },
  {
    value: 'FINDING',
    entityPrefix: 'finding',
    label: 'Finding',
    eventTypes: LIFECYCLE_EVENT_TYPES,
  },
  {
    value: 'ASSET',
    entityPrefix: 'endpoint',
    label: 'Asset',
    eventTypes: LIFECYCLE_EVENT_TYPES,
  },
  {
    value: 'ASSET_GROUP',
    entityPrefix: 'asset_group',
    label: 'Asset group',
    eventTypes: LIFECYCLE_EVENT_TYPES,
  },
  {
    value: 'TEAM',
    entityPrefix: 'team',
    label: 'Team',
    eventTypes: LIFECYCLE_EVENT_TYPES,
  },
  {
    value: 'PLAYER',
    entityPrefix: 'user',
    label: 'Player',
    eventTypes: LIFECYCLE_EVENT_TYPES,
  },
  {
    value: 'PAYLOAD',
    entityPrefix: 'payload',
    label: 'Payload',
    eventTypes: LIFECYCLE_EVENT_TYPES,
  },
  {
    value: 'VULNERABILITY',
    entityPrefix: 'vulnerability',
    label: 'Vulnerability',
    eventTypes: LIFECYCLE_EVENT_TYPES,
  },
  {
    value: 'SECURITY_PLATFORM',
    entityPrefix: 'security_platform',
    label: 'Security platform',
    eventTypes: LIFECYCLE_EVENT_TYPES,
  },
  {
    value: 'DOCUMENT',
    entityPrefix: 'document',
    label: 'Document',
    eventTypes: LIFECYCLE_EVENT_TYPES,
  },
  {
    value: 'CHALLENGE',
    entityPrefix: 'challenge',
    label: 'Challenge',
    eventTypes: LIFECYCLE_EVENT_TYPES,
  },
  {
    value: 'CATALOG_SYNC',
    label: 'Catalog sync',
    eventTypes: ['SYNC_BLOCKED'],
  },
];

export const resourceTypeLabel = (value?: string) =>
  TRIGGER_RESOURCE_TYPES.find(option => option.value === value)?.label ?? value ?? '-';

export const resourceTypeEntityPrefix = (value?: string) =>
  TRIGGER_RESOURCE_TYPES.find(option => option.value === value)?.entityPrefix;

export const eventTypesOf = (resourceType?: string): TriggerEventType[] =>
  TRIGGER_RESOURCE_TYPES.find(option => option.value === resourceType)?.eventTypes ?? LIFECYCLE_EVENT_TYPES;

const EVENT_TYPE_LABELS: Record<string, string> = {
  SCORE_DEGRADATION: 'Score degradation',
  SYNC_BLOCKED: 'Sync blocked',
};

export const eventTypeLabel = (eventType: string) =>
  EVENT_TYPE_LABELS[eventType] ?? eventType.charAt(0) + eventType.slice(1).toLowerCase();

export const DIGEST_PERIODS = ['HOUR', 'DAY', 'WEEK', 'MONTH'] as const;

export const WEEK_DAYS = [
  {
    value: '1',
    label: 'Monday',
  },
  {
    value: '2',
    label: 'Tuesday',
  },
  {
    value: '3',
    label: 'Wednesday',
  },
  {
    value: '4',
    label: 'Thursday',
  },
  {
    value: '5',
    label: 'Friday',
  },
  {
    value: '6',
    label: 'Saturday',
  },
  {
    value: '7',
    label: 'Sunday',
  },
];

/** Splits a stored trigger time ("HH:mm" or "<day>-HH:mm") into day + time parts. */
export const parseTriggerTime = (triggerTime?: string): {
  day: string;
  time: string;
} => {
  if (!triggerTime) {
    return {
      day: '1',
      time: '09:00',
    };
  }
  if (triggerTime.includes('-')) {
    const [day, time] = triggerTime.split('-');
    return {
      day,
      time,
    };
  }
  return {
    day: '1',
    time: triggerTime,
  };
};

export const buildTriggerTime = (period: string, day: string, time: string): string | undefined => {
  switch (period) {
    case 'DAY':
      return time;
    case 'WEEK':
    case 'MONTH':
      return `${day}-${time}`;
    default:
      return undefined;
  }
};

import { type ChipSeverity } from '@filigran/design-system';

import { type NotificationTriggerInput } from '../../../../utils/api-types';
import { operationLabel } from '../notifications/notificationUtils';

export type TriggerEventType = NonNullable<NotificationTriggerInput['notification_trigger_event_types']>[number];

const LIFECYCLE_EVENT_TYPES: TriggerEventType[] = ['CREATE', 'UPDATE', 'DELETE'];

/** Curated resource types watchable by notification triggers, with the events each one sends (mirrors backend catalog). */
export interface TriggerResourceTypeOption {
  value: string;
  entityPrefix?: string;
  label: string;
  eventTypes: TriggerEventType[];
  // Offered only with the catalogue preview on.
  catalogEventTypes?: TriggerEventType[];
}

export const TRIGGER_RESOURCE_TYPES: TriggerResourceTypeOption[] = [
  {
    value: 'SCENARIO',
    entityPrefix: 'scenario',
    label: 'Scenario',
    eventTypes: [...LIFECYCLE_EVENT_TYPES, 'SCORE_DEGRADATION'],
    catalogEventTypes: ['PAYLOAD_REVOKED', 'NEW_PAYLOAD_VERSION'],
  },
  {
    value: 'SIMULATION',
    entityPrefix: 'exercise',
    label: 'Simulation',
    eventTypes: [...LIFECYCLE_EVENT_TYPES, 'CRITICAL_ASSET_REACHED'],
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
    catalogEventTypes: ['REVOKED', 'REVOCATION_LIFTED', 'BASE_UPDATE_AVAILABLE'],
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
    label: 'Filigran catalog',
    eventTypes: [],
    catalogEventTypes: ['RELEASE_RECEIVED', 'INSTALLED_AUTOMATICALLY', 'SYNC_BLOCKED'],
  },
];

const optionOf = (value?: string) => TRIGGER_RESOURCE_TYPES.find(option => option.value === value);

export const resourceTypeLabel = (value?: string) => optionOf(value)?.label ?? value ?? '-';

export const resourceTypeEntityPrefix = (value?: string) => optionOf(value)?.entityPrefix;

export const eventTypesOf = (resourceType: string | undefined, catalogEnabled: boolean): TriggerEventType[] => {
  const option = optionOf(resourceType);
  if (!option) return LIFECYCLE_EVENT_TYPES;
  return catalogEnabled ? [...option.eventTypes, ...(option.catalogEventTypes ?? [])] : option.eventTypes;
};

export const triggerResourceTypes = (catalogEnabled: boolean) =>
  TRIGGER_RESOURCE_TYPES.filter(option => eventTypesOf(option.value, catalogEnabled).length > 0);

export const eventTypeLabel = (eventType: string) => (LIFECYCLE_EVENT_TYPES.includes(eventType as TriggerEventType)
  ? eventType.charAt(0) + eventType.slice(1).toLowerCase()
  : operationLabel(eventType));

/** Chip severity of a trigger event type; every event type of TRIGGER_RESOURCE_TYPES has an explicit case. */
export const eventTypeSeverity = (eventType: string): ChipSeverity => {
  switch (eventType) {
    case 'CREATE':
    case 'REVOCATION_LIFTED':
    case 'INSTALLED_AUTOMATICALLY':
      return 'low';
    case 'UPDATE':
    case 'RELEASE_RECEIVED':
      return 'info';
    case 'DELETE':
      return 'critical';
    case 'SCORE_DEGRADATION':
    case 'NEW_PAYLOAD_VERSION':
    case 'BASE_UPDATE_AVAILABLE':
      return 'medium';
    case 'PAYLOAD_REVOKED':
    case 'REVOKED':
      return 'high';
    case 'SYNC_BLOCKED':
      return 'critical';
    case 'CRITICAL_ASSET_REACHED':
      return 'critical';
    default:
      return 'neutral';
  }
};

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

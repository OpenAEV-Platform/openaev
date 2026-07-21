/** Curated resource types watchable by notification triggers (mirrors backend catalog). */
export interface TriggerResourceTypeOption {
  value: string;
  entityPrefix: string;
  label: string;
}

export const TRIGGER_RESOURCE_TYPES: TriggerResourceTypeOption[] = [
  {
    value: 'SCENARIO',
    entityPrefix: 'scenario',
    label: 'Scenario',
  },
  {
    value: 'SIMULATION',
    entityPrefix: 'exercise',
    label: 'Simulation',
  },
  {
    value: 'INJECT',
    entityPrefix: 'inject',
    label: 'Inject',
  },
  {
    value: 'FINDING',
    entityPrefix: 'finding',
    label: 'Finding',
  },
  {
    value: 'ASSET',
    entityPrefix: 'endpoint',
    label: 'Endpoint',
  },
  {
    value: 'ASSET_GROUP',
    entityPrefix: 'asset_group',
    label: 'Asset group',
  },
  {
    value: 'TEAM',
    entityPrefix: 'team',
    label: 'Team',
  },
  {
    value: 'PLAYER',
    entityPrefix: 'user',
    label: 'Player',
  },
  {
    value: 'PAYLOAD',
    entityPrefix: 'payload',
    label: 'Payload',
  },
  {
    value: 'VULNERABILITY',
    entityPrefix: 'vulnerability',
    label: 'Vulnerability',
  },
  {
    value: 'SECURITY_PLATFORM',
    entityPrefix: 'security_platform',
    label: 'Security platform',
  },
  {
    value: 'DOCUMENT',
    entityPrefix: 'document',
    label: 'Document',
  },
  {
    value: 'CHALLENGE',
    entityPrefix: 'challenge',
    label: 'Challenge',
  },
];

export const resourceTypeLabel = (value?: string) =>
  TRIGGER_RESOURCE_TYPES.find(option => option.value === value)?.label ?? value ?? '-';

export const resourceTypeEntityPrefix = (value?: string) =>
  TRIGGER_RESOURCE_TYPES.find(option => option.value === value)?.entityPrefix;

export const TRIGGER_EVENT_TYPES = ['CREATE', 'UPDATE', 'DELETE'] as const;

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

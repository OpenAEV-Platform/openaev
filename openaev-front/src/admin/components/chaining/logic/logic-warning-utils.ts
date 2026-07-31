import { collectEventFields } from './logic-flow-helpers';
import type { EventMeta } from './types';
import type { OutputProvidersMap } from './useOutputProviders';

export interface UnprovisionedLogicWarningItem {
  eventId: string;
  eventName: string;
  field: string;
}

export const findUnprovisionedLogicWarningItems = (
  eventMetas: Record<string, EventMeta>,
  providers: OutputProvidersMap,
): UnprovisionedLogicWarningItem[] => {
  const items: UnprovisionedLogicWarningItem[] = [];

  for (const meta of Object.values(eventMetas)) {
    const allFields = meta.formData.conditionGroups.flatMap(collectEventFields);
    const reportedKeys = new Set<string>();

    for (const field of allFields) {
      if (!field) continue;

      const key = `${meta.eventId}::${field}`;
      if (!providers[field] && !reportedKeys.has(key)) {
        reportedKeys.add(key);
        items.push({
          eventId: meta.eventId,
          eventName: meta.formData.name,
          field,
        });
      }
    }
  }

  return items;
};

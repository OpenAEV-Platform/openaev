import {
  AbilityBuilder,
  createMongoAbility,
  type MongoAbility,
} from '@casl/ability';

import parseCapability, { parseGrants } from './parserRbac';
import { type Actions, type Subjects } from './types';

export type AppAbility = MongoAbility<[Actions, Subjects]>;

// TODO : Delete isAdmin when we remove this logic
export function defineAbility(capabilities: string[], grants: Record<string, string>, isAdmin: boolean): (AppAbility) {
  const { can, rules } = new AbilityBuilder<AppAbility>(createMongoAbility);
  if (isAdmin) {
    // eslint-disable-next-line @typescript-eslint/ban-ts-comment
    // @ts-expect-error
    can('manage', 'all');
  }
  for (const cap of capabilities) {
    if (cap === 'BYPASS') {
      // We ignore ts here to accept lowercase which are CASL default keys
      // eslint-disable-next-line @typescript-eslint/ban-ts-comment
      // @ts-expect-error
      can('manage', 'all'); // "manage" in lowercase means all actions, "all" means all subject
      continue;
    }

    const parsed = parseCapability(cap);
    if (parsed) {
      const [action, subject] = parsed;
      can(action, subject);
    }
  }

  // To use casl for grant : ability.can(ACTIONS.MANAGE, SUBJECTS.RESOURCE, scenario.scenario_id)
  const parsedGrants = parseGrants(grants);
  if (parsedGrants) {
    for (const [action, subject, conditions] of parsedGrants) {
      can(action, subject, conditions);
    }
  }

  return createMongoAbility(rules);
}

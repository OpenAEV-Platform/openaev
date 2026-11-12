import { type UserGrant } from '../api-types';
import { ACTIONS, type Actions, SUBJECTS, type Subjects } from './types';

export default function parseCapability(cap: string): [Actions, Subjects] | null {
  const parts = cap.split('_');
  if (parts.length < 2) return null;

  // For example if we take "ACCESS_ATOMIC_TESTING" the first word is the action, the followings are the subject
  const action = parts[0] as Actions;
  const subject = parts.slice(1).join('_');

  // To avoid any mistake : check that the action or subject is one of those declared in the types.ts file
  if (!Object.values(ACTIONS).includes(action as Actions)) return null;
  if (!Object.values(SUBJECTS).includes(subject as Subjects)) return null;

  return [action, subject as Subjects];
}

const ROLE_TO_ACTION: Record<string, Actions[]> = {
  LAUNCHER: [ACTIONS.LAUNCH, ACTIONS.MANAGE, ACTIONS.ACCESS, ACTIONS.DELETE],
  PLANNER: [ACTIONS.MANAGE, ACTIONS.ACCESS, ACTIONS.DELETE],
  OBSERVER: [ACTIONS.ACCESS],
};

type ParsedGrantByAction = [Actions, Subjects, string[] ] [];

// Grant resource types that live in the Threat Arsenal (PAYLOAD is the deprecated name)
const THREAT_ARSENAL_RESOURCE_TYPES: UserGrant['type'][] = ['THREAT_ARSENAL', 'PAYLOAD'];

export function parseGrants(grants: Record<string, UserGrant>): ParsedGrantByAction | null {
  const grouped: Partial<Record<Subjects, Partial<Record<Actions, string[]>>>> = {};
  const add = (subject: Subjects, action: Actions, id: string) => {
    const bySubject = grouped[subject] ?? {};
    grouped[subject] = bySubject;
    bySubject[action] = [...(bySubject[action] ?? []), id];
  };

  for (const [id, { grant, type }] of Object.entries(grants)) {
    const actions = ROLE_TO_ACTION[grant];
    if (!actions) {
      return null;
    }
    for (const action of actions) {
      // Use "RESOURCE" as a generic subject for grants
      add(SUBJECTS.RESOURCE, action, id);
      if (THREAT_ARSENAL_RESOURCE_TYPES.includes(type)) {
        add(SUBJECTS.THREAT_ARSENAL_RESOURCE, action, id);
      }
    }
  }

  return Object.entries(grouped).flatMap(([subject, byAction]) => Object.entries(byAction).map(
    ([action, ids]): [Actions, Subjects, string[]] => [action as Actions, subject as Subjects, ids],
  ));
}

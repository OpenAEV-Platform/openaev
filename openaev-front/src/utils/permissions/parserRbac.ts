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

export function parseGrants(grants: Record<string, string>): ParsedGrantByAction | null {
  const grouped: Record<string, string[]> = {};

  for (const [id, role] of Object.entries(grants)) {
    const actions = ROLE_TO_ACTION[role];
    if (!actions) {
      return null;
    }
    for (const action of actions) {
      if (!grouped[action]) {
        grouped[action] = [];
      }
      grouped[action].push(id);
    }
  }

  // Use "RESOURCE" as a generic subject for grants
  return Object.entries(grouped).map(([action, ids]) => [
    action as Actions,
    SUBJECTS.RESOURCE, ids,
  ]);
}

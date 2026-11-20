import { ACTIONS, type Actions, SUBJECTS, type Subjects } from '../../../../utils/permissions/types';

/**
 * Checks that open the scenario, simulation and atomic testing lists: the capability, or a grant
 * on at least one of them, so users with grants only still reach what is granted to them.
 * Used by ProtectedRoute to guard the three list routes and by the left menu to show their entries.
 */
export const ASSESSMENT_ACCESS_CHECKS: {
  action: Actions;
  subject: Subjects;
}[] = [
  {
    action: ACTIONS.ACCESS,
    subject: SUBJECTS.ASSESSMENT,
  },
  {
    action: ACTIONS.ACCESS,
    subject: SUBJECTS.ASSESSMENT_RESOURCE,
  },
];

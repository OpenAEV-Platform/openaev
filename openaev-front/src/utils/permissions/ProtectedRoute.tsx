import { type JSX, useContext } from 'react';
import { useParams } from 'react-router';

import NoAccess from './NoAccess';
import { AbilityContext } from './PermissionsProvider';
import type { Actions, Subjects } from './types';

type GrantCheck = {
  action: Actions;
  subject: Subjects;
  resourceURIParamName?: string;
};

type ProtectedRouteProps = {
  Component: JSX.Element;
  checks: GrantCheck[];
};

const ProtectedRoute = ({ checks, Component }: ProtectedRouteProps) => {
  const ability = useContext(AbilityContext);
  const params = useParams();

  const grantedFor = checks.some(
    ({ action, subject, resourceURIParamName }) => {
      let resourceId;
      if (resourceURIParamName) {
        resourceId = params[resourceURIParamName];
      }
      if (resourceId) {
        return ability.can(action, subject, resourceId);
      }
      return ability.can(action, subject);
    },
  );

  if (!grantedFor) {
    return (
      <NoAccess />
    );
  }
  return Component;
};

export default ProtectedRoute;

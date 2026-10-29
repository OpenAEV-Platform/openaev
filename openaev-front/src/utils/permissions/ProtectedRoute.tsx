import { type JSX } from 'react';
import { useParams } from 'react-router';

import useEnterpriseEdition from '../hooks/useEnterpriseEdition';
import NoAccess from './NoAccess';
import NoEnterpriseEdition from './NoEnterpriseEdition';
import { useAbility } from './permissionsContext';
import type { Actions, Subjects } from './types';

type GrantCheck = {
  action: Actions;
  subject: Subjects;
  resourceURIParamName?: string;
};

type ProtectedRouteProps = {
  Component: JSX.Element;
  checks: GrantCheck[];
  requireEE?: boolean;
};

const ProtectedRoute = ({ checks, Component, requireEE = false }: ProtectedRouteProps) => {
  const ability = useAbility();
  const params = useParams();
  const { isValidated: isEnterpriseEdition } = useEnterpriseEdition();

  if (requireEE && !isEnterpriseEdition) {
    return <NoEnterpriseEdition />;
  }

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

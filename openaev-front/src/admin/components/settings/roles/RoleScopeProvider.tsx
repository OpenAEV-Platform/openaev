import { type FunctionComponent, type ReactNode } from 'react';

import { type CapabilityScope } from '../../../../utils/permissions/types';
import { ROLE_SCOPES, RoleScopeContext } from './RoleScopeContext';

interface Props {
  scope: CapabilityScope;
  children: ReactNode;
}

const RoleScopeProvider: FunctionComponent<Props> = ({ scope, children }) => (
  <RoleScopeContext.Provider value={ROLE_SCOPES[scope]}>{children}</RoleScopeContext.Provider>
);

export default RoleScopeProvider;

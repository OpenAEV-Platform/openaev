import { useContext } from 'react';
import { Navigate, Route, Routes } from 'react-router';

import { errorWrapper } from '../../../components/Error';
import NotFound from '../../../components/NotFound';
import { AbilityContext } from '../../../utils/permissions/permissionsContext';
import ProtectedRoute from '../../../utils/permissions/ProtectedRoute';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import LessonsTemplateIndex from '../components/lessons/Index';
import LessonsTemplates from '../components/lessons/LessonsTemplates';
import Tenants from '../platform/tenants/Tenants';
import AttackPatterns from './attack_patterns/AttackPatterns';
import AutonomousAttackSettings from './autonomous_attack/AutonomousAttackSettings';
import CustomDomains from './custom_domains/CustomDomains';
import XlsMappers from './data_ingestion/XlsMappers';
import Experience from './experience/Experience';
import Groups from './groups/Groups';
import KillChainPhases from './kill_chain_phases/KillChainPhases';
import Notifiers from './notifiers/Notifiers';
import Organizations from './organizations/Organizations';
import Policies from './policies/Policies';
import Roles from './roles/Roles';
import GroupDetail from './security_detail/GroupDetail';
import OrganizationDetail from './security_detail/OrganizationDetail';
import RoleDetail from './security_detail/RoleDetail';
import UserDetail from './security_detail/UserDetail';
import Sessions from './sessions/Sessions';
import TagRules from './tag_rules/TagRules';
import Tags from './tags/Tags';
import TenantParameters from './TenantParameters';
import Users from './users/Users';
import useSecurityScope from './useSecurityScope';
import Vulnerabilities from './vulnerabilities/Vulnerabilities';

const SECURITY_USERS_CHECKS = [
  {
    action: ACTIONS.ACCESS,
    subject: SUBJECTS.TENANT_USERS_GROUPS_AND_ROLES,
  },
  {
    action: ACTIONS.ACCESS,
    subject: SUBJECTS.PLATFORM_USERS_GROUPS_AND_ROLES,
  },
];

const TENANT_SETTINGS_CHECKS = [{
  action: ACTIONS.ACCESS,
  subject: SUBJECTS.TENANT_SETTINGS,
}];

const SecurityLanding = () => {
  const {
    canAccessTenantSettings,
    canAccessTenantUsers,
    canAccessPlatformUsers,
    canAccessSession,
  } = useSecurityScope();
  const ability = useContext(AbilityContext);
  // The landing arbitrates between both scopes, so each one is named explicitly.
  const canManageSessions = canAccessSession('TENANT');
  const canManagePlatformSessions = canAccessSession('PLATFORM');
  if (canAccessTenantUsers || canAccessPlatformUsers) {
    return <Navigate to="users" replace={true} />;
  }
  if (canAccessTenantSettings) {
    return <Navigate to="organizations" replace={true} />;
  }
  if (ability.can(ACTIONS.ACCESS, SUBJECTS.TENANTS)) {
    return <Navigate to="tenants" replace={true} />;
  }
  if (canManageSessions || canManagePlatformSessions) {
    return <Navigate to={canManageSessions ? 'sessions' : 'sessions?scope=platform'} replace={true} />;
  }
  // Nothing reachable: let the users route answer with its own NoAccess.
  return <Navigate to="users" replace={true} />;
};

const Index = () => {
  return (
    <Routes>
      <Route path="" element={<Navigate to="parameters" replace={true} />} />
      <Route path="parameters" element={errorWrapper(TenantParameters)()} />
      <Route path="security" element={<SecurityLanding />} />
      <Route
        path="security/groups"
        element={(
          <ProtectedRoute
            checks={SECURITY_USERS_CHECKS}
            Component={errorWrapper(Groups)()}
          />
        )}
      />
      <Route
        path="security/groups/:groupId"
        element={(
          <ProtectedRoute
            checks={SECURITY_USERS_CHECKS}
            Component={errorWrapper(GroupDetail)()}
          />
        )}
      />
      <Route
        path="security/users"
        element={(
          <ProtectedRoute
            checks={SECURITY_USERS_CHECKS}
            Component={errorWrapper(Users)()}
          />
        )}
      />
      <Route
        path="security/users/:userId"
        element={(
          <ProtectedRoute
            checks={SECURITY_USERS_CHECKS}
            Component={errorWrapper(UserDetail)()}
          />
        )}
      />
      <Route
        path="security/roles"
        element={(
          <ProtectedRoute
            checks={SECURITY_USERS_CHECKS}
            Component={errorWrapper(Roles)()}
          />
        )}
      />
      <Route
        path="security/roles/:roleId"
        element={(
          <ProtectedRoute
            checks={SECURITY_USERS_CHECKS}
            Component={errorWrapper(RoleDetail)()}
          />
        )}
      />
      <Route
        path="security/organizations"
        element={(
          <ProtectedRoute
            checks={TENANT_SETTINGS_CHECKS}
            Component={errorWrapper(Organizations)()}
          />
        )}
      />
      <Route
        path="security/organizations/:organizationId"
        element={(
          <ProtectedRoute
            checks={TENANT_SETTINGS_CHECKS}
            Component={errorWrapper(OrganizationDetail)()}
          />
        )}
      />
      <Route
        path="security/sessions"
        element={(
          <ProtectedRoute
            checks={[
              {
                action: ACTIONS.MANAGE,
                subject: SUBJECTS.SESSIONS,
              },
              {
                action: ACTIONS.MANAGE,
                subject: SUBJECTS.PLATFORM_SESSIONS,
              },
            ]}
            Component={errorWrapper(Sessions)()}
          />
        )}
      />
      <Route
        path="security/tenants"
        element={(
          <ProtectedRoute
            checks={[{
              action: ACTIONS.ACCESS,
              subject: SUBJECTS.TENANTS,
            }]}
            requireEE
            Component={errorWrapper(Tenants)()}
          />
        )}
      />
      <Route path="security/policies" element={errorWrapper(Policies)()} />
      <Route path="taxonomies" element={<Navigate to="tags" replace={true} />} />
      <Route path="taxonomies/tags" element={errorWrapper(Tags)()} />
      <Route path="taxonomies/attack_patterns" element={errorWrapper(AttackPatterns)()} />
      <Route path="taxonomies/kill_chain_phases" element={errorWrapper(KillChainPhases)()} />
      <Route path="taxonomies/vulnerabilities" element={errorWrapper(Vulnerabilities)()} />
      <Route path="data_ingestion" element={<Navigate to="xls_mappers" replace={true} />} />
      <Route path="data_ingestion/xls_mappers" element={errorWrapper(XlsMappers)()} />
      {/* Customization section (OpenCTI-aligned): asset rules + notifiers
          behind a shared right submenu (CustomizationMenu). */}
      <Route path="customization" element={<Navigate to="asset_rules" replace={true} />} />
      <Route path="customization/asset_rules" element={errorWrapper(TagRules)()} />
      <Route path="customization/custom_domains" element={errorWrapper(CustomDomains)()} />
      <Route path="customization/notifiers" element={errorWrapper(Notifiers)()} />
      <Route
        path="customization/lessons"
        element={(
          <ProtectedRoute
            checks={[{
              action: ACTIONS.ACCESS,
              subject: SUBJECTS.LESSONS_LEARNED,
            }]}
            Component={errorWrapper(LessonsTemplates)()}
          />
        )}
      />
      <Route
        path="customization/lessons/:lessonsTemplateId/*"
        element={(
          <ProtectedRoute
            checks={[{
              action: ACTIONS.ACCESS,
              subject: SUBJECTS.LESSONS_LEARNED,
            }]}
            Component={errorWrapper(LessonsTemplateIndex)()}
          />
        )}
      />
      <Route path="customization/autonomous_attack" element={errorWrapper(AutonomousAttackSettings)()} />
      {/* Legacy flat paths kept as redirects so old bookmarks keep working. */}
      <Route path="asset_rules" element={<Navigate to="../customization/asset_rules" replace={true} />} />
      <Route path="notifiers" element={<Navigate to="../customization/notifiers" replace={true} />} />
      <Route path="experience" element={errorWrapper(Experience)()} />

      {/* Not found */}
      <Route path="*" element={<NotFound />} />
    </Routes>
  );
};

export default Index;

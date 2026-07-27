import { Navigate, Route, Routes } from 'react-router';

import { errorWrapper } from '../../../components/Error';
import NotFound from '../../../components/NotFound';
import ProtectedRoute from '../../../utils/permissions/ProtectedRoute';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import Tenants from '../platform/tenants/Tenants';
import AttackPatterns from './attack_patterns/AttackPatterns';
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
import Vulnerabilities from './vulnerabilities/Vulnerabilities';

const Index = () => {
  return (
    <Routes>
      <Route path="" element={<Navigate to="parameters" replace={true} />} />
      <Route path="parameters" element={errorWrapper(TenantParameters)()} />
      <Route path="security" element={<Navigate to="users" replace={true} />} />
      <Route path="security/groups" element={errorWrapper(Groups)()} />
      <Route path="security/groups/:groupId" element={errorWrapper(GroupDetail)()} />
      <Route path="security/users" element={errorWrapper(Users)()} />
      <Route path="security/users/:userId" element={errorWrapper(UserDetail)()} />
      <Route path="security/roles" element={errorWrapper(Roles)()} />
      <Route path="security/roles/:roleId" element={errorWrapper(RoleDetail)()} />
      <Route path="security/organizations" element={errorWrapper(Organizations)()} />
      <Route path="security/organizations/:organizationId" element={errorWrapper(OrganizationDetail)()} />
      <Route
        path="security/sessions"
        element={(
          <ProtectedRoute
            checks={[
              {
                action: ACTIONS.ACCESS,
                subject: SUBJECTS.TENANT_SETTINGS,
              },
              {
                action: ACTIONS.ACCESS,
                subject: SUBJECTS.PLATFORM_USERS_GROUPS_AND_ROLES,
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
      <Route path="customization/notifiers" element={errorWrapper(Notifiers)()} />
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

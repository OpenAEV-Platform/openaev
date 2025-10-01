import { Navigate, Route, Routes } from 'react-router';

import { errorWrapper } from '../../../components/Error';
import NotFound from '../../../components/NotFound';
import Organizations from '../teams/Organizations';
import AttackPatterns from './attack_patterns/AttackPatterns';
import Cves from './cves/Cves';
import XlsMappers from './data_ingestion/XlsMappers';
import Experience from './experience/Experience';
import Groups from './groups/Groups';
import KillChainPhases from './kill_chain_phases/KillChainPhases';
import Parameters from './Parameters';
import Policies from './policies/Policies';
import Roles from './roles/Roles';
import TagRules from './tag_rules/TagRules';
import Tags from './tags/Tags';
import Users from './users/Users';

const Index = () => {
  return (
    <Routes>
      <Route path="" element={<Navigate to="parameters" replace={true} />} />
      <Route path="parameters" element={errorWrapper(Parameters)()} />
      <Route path="security" element={<Navigate to="roles" replace={true} />} />
      <Route path="security/groups" element={errorWrapper(Groups)()} />
      <Route path="security/users" element={errorWrapper(Users)()} />
      <Route path="security/roles" element={errorWrapper(Roles)()} />
      <Route path="security/organizations" element={errorWrapper(Organizations)()} />
      <Route path="security/policies" element={errorWrapper(Policies)()} />
      <Route path="taxonomies" element={<Navigate to="tags" replace={true} />} />
      <Route path="taxonomies/tags" element={errorWrapper(Tags)()} />
      <Route path="taxonomies/attack_patterns" element={errorWrapper(AttackPatterns)()} />
      <Route path="taxonomies/kill_chain_phases" element={errorWrapper(KillChainPhases)()} />
      <Route path="taxonomies/cves" element={errorWrapper(Cves)()} />
      <Route path="data_ingestion" element={<Navigate to="xls_mappers" replace={true} />} />
      <Route path="data_ingestion/xls_mappers" element={errorWrapper(XlsMappers)()} />
      <Route path="asset_rules" element={errorWrapper(TagRules)()} />
      <Route path="experience" element={errorWrapper(Experience)()} />

      {/* Not found */}
      <Route path="*" element={<NotFound />} />
    </Routes>
  );
};

export default Index;

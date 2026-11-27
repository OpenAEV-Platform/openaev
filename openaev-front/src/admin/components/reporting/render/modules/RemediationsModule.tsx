import { type FunctionComponent } from 'react';

import { type ReportingModule } from '../../../../../utils/api-types';
import InjectGuidanceModule from './InjectGuidanceModule';

// PROTOTYPE - MOCK DATA ONLY. Shown inject by inject (see InjectGuidanceModule). When the report has both the
// Mitigations and the Remediations modules, the page shows one section with both (see ReportingRenderPage).
const RemediationsModule: FunctionComponent<{ module: ReportingModule }> = ({ module }) => (
  <InjectGuidanceModule module={module} showMitigation={false} showRemediation={true} />
);

export default RemediationsModule;

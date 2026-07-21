import {
  BugReportOutlined,
  CloudOutlined,
  DevicesOtherOutlined,
  ForumOutlined,
  GpsFixedOutlined,
  GroupsOutlined,
  Inventory2Outlined,
  LabelOutlined,
  ShieldOutlined,
  SmartToyOutlined,
  StorageOutlined,
  TravelExploreOutlined,
} from '@mui/icons-material';
import { type ComponentType } from 'react';

import { prettifyUseCase } from './catalog-facets';

// Known catalog use cases get a dedicated icon; unknown / future ones fall
// back to a generic label icon. Keys are the prettified (lowercase, spaces)
// form so raw values like SECURITY_RESPONSE or security-response both match.
const USE_CASE_ICONS: Record<string, ComponentType<{ sx?: object }>> = {
  'ai security': SmartToyOutlined,
  'asset collection': DevicesOtherOutlined,
  'attack simulation': GpsFixedOutlined,
  'cloud security': CloudOutlined,
  'communication': ForumOutlined,
  'data collection': StorageOutlined,
  'payload collection': Inventory2Outlined,
  'player collection': GroupsOutlined,
  'reconnaissance': TravelExploreOutlined,
  'security response': ShieldOutlined,
  'vulnerability scanning': BugReportOutlined,
};

const useCaseIcon = (useCase: string): ComponentType<{ sx?: object }> => {
  // prettifyUseCase now returns a sentence-cased label ("Ai security"), while the
  // icon keys are lowercase - normalize before lookup so icons keep matching.
  return USE_CASE_ICONS[prettifyUseCase(useCase).toLowerCase()] ?? LabelOutlined;
};

export default useCaseIcon;

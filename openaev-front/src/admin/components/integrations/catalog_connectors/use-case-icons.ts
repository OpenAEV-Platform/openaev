import {
  DevicesOtherOutlined,
  DnsOutlined,
  GroupsOutlined,
  Inventory2Outlined,
  LabelOutlined,
  ShieldOutlined,
  StorageOutlined,
  TuneOutlined,
} from '@mui/icons-material';
import { type ComponentType } from 'react';

import { prettifyUseCase } from './catalog-facets';

// Known catalog use cases get a dedicated icon; unknown / future ones fall
// back to a generic label icon. Keys are the prettified (lowercase, spaces)
// form so raw values like SECURITY_RESPONSE or security-response both match.
const USE_CASE_ICONS: Record<string, ComponentType<{ sx?: object }>> = {
  'asset collection': DevicesOtherOutlined,
  'data collection': StorageOutlined,
  'endpoint collection': DnsOutlined,
  'payload collection': Inventory2Outlined,
  'player collection': GroupsOutlined,
  'security response': ShieldOutlined,
  'technical': TuneOutlined,
};

const useCaseIcon = (useCase: string): ComponentType<{ sx?: object }> => {
  return USE_CASE_ICONS[prettifyUseCase(useCase)] ?? LabelOutlined;
};

export default useCaseIcon;

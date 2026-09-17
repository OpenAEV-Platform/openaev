import {
  AccountTreeOutlined,
  BugReportOutlined,
  FolderOutlined,
  HelpOutline,
  InfoOutlined,
  KeyOutlined,
  PersonOutline,
  PublicOutlined,
  SettingsOutlined,
} from '@mui/icons-material';
import { type SvgIconProps } from '@mui/material';
import { type ComponentType } from 'react';

export type FindingAggregationCategory
  = | 'SURFACE_REACHABILITY'
    | 'IDENTITIES'
    | 'CREDENTIAL_ACCESS'
    | 'PRIVILEGE_TRUST_STRUCTURE'
    | 'EXPLOITABLE_WEAKNESSES'
    | 'RESOURCES'
    | 'CONFIGURATION_POSTURE'
    | 'INFORMATIVE';

interface FindingAggregationCategoryDefinition {
  value: FindingAggregationCategory;
  label: string;
  icon: ComponentType<SvgIconProps>;
}

export const ACTIONABLE_FINDING_CATEGORIES: FindingAggregationCategoryDefinition[] = [
  {
    value: 'SURFACE_REACHABILITY',
    label: 'Surface & Reachability',
    icon: PublicOutlined,
  },
  {
    value: 'IDENTITIES',
    label: 'Identities',
    icon: PersonOutline,
  },
  {
    value: 'CREDENTIAL_ACCESS',
    label: 'Credential Access',
    icon: KeyOutlined,
  },
  {
    value: 'PRIVILEGE_TRUST_STRUCTURE',
    label: 'Privilege & Trust Structure',
    icon: AccountTreeOutlined,
  },
  {
    value: 'EXPLOITABLE_WEAKNESSES',
    label: 'Exploitable Weaknesses',
    icon: BugReportOutlined,
  },
  {
    value: 'RESOURCES',
    label: 'Resources',
    icon: FolderOutlined,
  },
  {
    value: 'CONFIGURATION_POSTURE',
    label: 'Configuration & Posture',
    icon: SettingsOutlined,
  },
];

export const INFORMATIVE_FINDING_CATEGORY: FindingAggregationCategoryDefinition = {
  value: 'INFORMATIVE',
  label: 'Informative',
  icon: InfoOutlined,
};

export const FINDING_AGGREGATION_CATEGORIES = [
  ...ACTIONABLE_FINDING_CATEGORIES,
  INFORMATIVE_FINDING_CATEGORY,
];

const UNKNOWN_FINDING_CATEGORY: FindingAggregationCategoryDefinition = {
  value: 'INFORMATIVE',
  label: 'Unknown category',
  icon: HelpOutline,
};

export const getFindingAggregationCategory = (
  value?: string,
): FindingAggregationCategoryDefinition =>
  FINDING_AGGREGATION_CATEGORIES.find(category => category.value === value)
  ?? UNKNOWN_FINDING_CATEGORY;

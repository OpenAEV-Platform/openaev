import { type ChipSeverity } from '@filigran/design-system';

import { useFormatter } from '../../../components/i18n';
import {
  type ApplyLayer,
  type AuthorRule,
  type CollectorType,
  type ControlType,
  type Platform,
  type RemediationMitigationCategory,
  type RemediationMitigationItem,
} from './remediationMitigationMock';

// Product, vendor and operating system names are proper nouns: they are never translated.
export const PLATFORM_LABELS: Record<Platform, string> = {
  windows: 'Windows',
  linux: 'Linux',
  macos: 'macOS',
};

export const COLLECTOR_LABELS: Record<CollectorType, string> = {
  splunk: 'Splunk',
  crowdstrike: 'CrowdStrike',
};

export const AUTHOR_SEVERITY: Record<AuthorRule, ChipSeverity> = {
  HUMAN: 'neutral',
  AI: 'medium',
  AI_OUTDATED: 'high',
};

// Every label is a literal t() call with its English text, so the i18n checker sees the key.
const useRemediationMitigationLabels = () => {
  const { t } = useFormatter();

  const category: Record<RemediationMitigationCategory, string> = {
    remediation: t('Remediation'),
    mitigation: t('Mitigation'),
  };

  const controlType: Record<ControlType, string> = {
    hardening: t('Hardening'),
    patch: t('Patch'),
    detection: t('Detection'),
    prevention: t('Prevention'),
  };

  const author: Record<AuthorRule, string> = {
    HUMAN: t('Written by a human'),
    AI: t('AI-generated, needs review'),
    AI_OUTDATED: t('AI-generated, outdated'),
  };

  const applyLayer: Record<ApplyLayer, string> = {
    host: t('Host'),
    directory_domain: t('Directory / domain'),
    application: t('Application'),
    network: t('Network'),
  };

  const scopeLabel = (item: RemediationMitigationItem) => (item.scope === 'vendor_specific' && item.collector_type
    ? `${t('Vendor-specific')} · ${COLLECTOR_LABELS[item.collector_type]}`
    : t('Generic / host-level'));

  // "Category · Control type", e.g. "Mitigation · Detection".
  const combinedTag = (item: RemediationMitigationItem) => `${category[item.category]} · ${controlType[item.control_type]}`;

  return {
    category,
    controlType,
    author,
    applyLayer,
    scopeLabel,
    combinedTag,
  };
};

export default useRemediationMitigationLabels;

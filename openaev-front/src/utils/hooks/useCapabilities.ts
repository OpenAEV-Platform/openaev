import { fetchCapabilities } from '../../actions/capabilities/capability-action';
import type { CapabilityHelper } from '../../actions/capabilities/capability-helper';
import { useHelper } from '../../store';
import type { CapabilityOutput } from '../api-types';
import { useAppDispatch } from '../hooks';
import useDataLoader from './useDataLoader';

const CAPABILITY_UI_ORDER = [
  'BYPASS',
  'DASHBOARDS',
  'REPORTINGS',
  'FINDINGS',
  'ASSESSMENT',
  'THREAT_ARSENALS',
  'TARGETS',
  'CONTENT',
  'PLATFORM_SETTINGS',
  'PLATFORM_USERS_GROUPS_AND_ROLES',
  'TENANTS',
  'TENANT_SETTINGS',
  'STIX',
  'SERVICE',
] as const;

const CAPABILITY_ORDER_INDEX: Map<string, number> = new Map(
  CAPABILITY_UI_ORDER.map((value, index) => [value, index]),
);

const sortCapabilitiesForUi = (rawCapabilities: CapabilityOutput[]): CapabilityOutput[] => {
  return [...rawCapabilities].sort((a, b) => {
    const aIndex = CAPABILITY_ORDER_INDEX.get(a.capability_value) ?? Number.MAX_SAFE_INTEGER;
    const bIndex = CAPABILITY_ORDER_INDEX.get(b.capability_value) ?? Number.MAX_SAFE_INTEGER;
    return aIndex - bIndex;
  });
};

const useCapabilities = (scope: 'PLATFORM' | 'TENANT') => {
  const dispatch = useAppDispatch();

  const { capabilities } = useHelper((helper: CapabilityHelper) => ({
    capabilities: scope === 'PLATFORM'
      ? helper.getPlatformCapabilities()
      : helper.getTenantCapabilities(),
  }));

  useDataLoader(() => {
    dispatch(fetchCapabilities(scope));
  });

  const orderedCapabilities = sortCapabilitiesForUi(capabilities as CapabilityOutput[]);
  const loading = orderedCapabilities.length === 0;

  return {
    capabilities: orderedCapabilities,
    loading,
  };
};

export default useCapabilities;

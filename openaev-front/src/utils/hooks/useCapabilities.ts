import { useState } from 'react';

import { fetchCapabilities } from '../../actions/capabilities/capability-action';
import type { CapabilityHelper } from '../../actions/capabilities/capability-helper';
import { useHelper } from '../../store';
import type { CapabilityOutput } from '../../utils/api-types';
import { useAppDispatch } from '../hooks';
import useDataLoader from './useDataLoader';

type CapabilitiesFetchResult = {
  entities?: {
    platform_capabilities?: Record<string, CapabilityOutput>;
    tenant_capabilities?: Record<string, CapabilityOutput>;
  };
  result?: string[];
};

const useCapabilities = (scope: 'PLATFORM' | 'TENANT') => {
  const dispatch = useAppDispatch();
  const [orderedIds, setOrderedIds] = useState<string[]>([]);

  const { capabilities, capabilitiesMap } = useHelper((helper: CapabilityHelper) => ({
    capabilities: scope === 'PLATFORM'
      ? helper.getPlatformCapabilities()
      : helper.getTenantCapabilities(),
    capabilitiesMap: scope === 'PLATFORM'
      ? helper.getPlatformCapabilitiesMap()
      : helper.getTenantCapabilitiesMap(),
  }));

  useDataLoader(() => {
    dispatch(fetchCapabilities(scope)).then((result: CapabilitiesFetchResult) => {
      setOrderedIds(Array.isArray(result?.result) ? result.result : []);
    });
  });

  const mapHasGet = (map: unknown): map is { get: (id: string) => unknown } => {
    return map !== null
      && typeof map === 'object'
      && 'get' in map
      && typeof (map as { get: unknown }).get === 'function';
  };

  const capabilityFromMap = (id: string): CapabilityOutput | undefined => {
    if (mapHasGet(capabilitiesMap)) {
      return capabilitiesMap.get(id) as CapabilityOutput | undefined;
    }
    return (capabilitiesMap as Record<string, CapabilityOutput> | undefined)?.[id];
  };

  let orderedCapabilities = capabilities as CapabilityOutput[];
  if (orderedIds.length > 0) {
    orderedCapabilities = orderedIds
      .map(capabilityFromMap)
      .filter(Boolean) as CapabilityOutput[];
  }

  const loading = orderedCapabilities.length === 0;

  return {
    capabilities: orderedCapabilities,
    loading,
  };
};

export default useCapabilities;

import { fetchCapabilities } from '../../actions/capabilities/capability-action';
import type { CapabilityHelper } from '../../actions/capabilities/capability-helper';
import { useHelper } from '../../store';
import type { CapabilityOutput } from '../api-types';
import { useAppDispatch } from '../hooks';
import useDataLoader from './useDataLoader';

const useCapabilities = (scope?: 'PLATFORM' | 'TENANT') => {
  const dispatch = useAppDispatch();

  const { capabilities } = useHelper((helper: CapabilityHelper) => ({ capabilities: helper.getCapabilities() }));

  useDataLoader(() => {
    dispatch(fetchCapabilities(scope));
  });

  const loading = capabilities.length === 0;

  return {
    capabilities: capabilities as CapabilityOutput[],
    loading,
  };
};

export default useCapabilities;

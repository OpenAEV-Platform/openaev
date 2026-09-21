import { useEffect, useMemo, useState } from 'react';

import { fetchPrimitiveTypeDescriptors } from '../../../../actions/payloads/primitive-type-actions';
import {
  buildDescriptorsByType,
  type DescriptorsByPrimitiveType,
  EMPTY_DESCRIPTORS,
  type PrimitiveTypeLabel,
} from './primitive-types';

type UsePrimitiveTypeDescriptorsResult = {
  descriptorsByType: DescriptorsByPrimitiveType;
  /** Every primitive type label, alphabetically ordered, ready to feed a type selector. */
  primitiveTypes: PrimitiveTypeLabel[];
  isLoading: boolean;
  error: Error | null;
};

// Shared across every consumer: the descriptors are the same for the whole session, and the
// condition editor renders one row per condition, each of which would otherwise refetch.
let descriptorsPromise: Promise<DescriptorsByPrimitiveType> | null = null;

const getDescriptors = (): Promise<DescriptorsByPrimitiveType> => {
  descriptorsPromise ??= fetchPrimitiveTypeDescriptors()
    .then(buildDescriptorsByType)
    .catch((error) => {
      // Drop the memoized rejection so a later mount can retry instead of replaying the failure.
      descriptorsPromise = null;
      throw error;
    });
  return descriptorsPromise;
};

/**
 * Loads the backend primitive-type descriptors, which drive both the operators offered by the
 * condition editor and the validation of the value the user types.
 *
 * While loading or after a failure, the map stays empty, which resolves every type to the
 * permissive text behavior: the form keeps working, it just stops narrowing the operator list.
 */
const usePrimitiveTypeDescriptors = (): UsePrimitiveTypeDescriptorsResult => {
  const [descriptorsByType, setDescriptorsByType] = useState<DescriptorsByPrimitiveType>(EMPTY_DESCRIPTORS);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);

  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      try {
        setIsLoading(true);
        setError(null);
        const loaded = await getDescriptors();
        if (!cancelled) setDescriptorsByType(loaded);
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err : new Error('Failed to fetch primitive type descriptors'));
        }
      } finally {
        if (!cancelled) setIsLoading(false);
      }
    };
    void load();
    return () => {
      cancelled = true;
    };
  }, []);

  // Sorted client side rather than relying on the backend ordering: the endpoint sorts by code
  // point, which places `ip_subnet` before `ipv4`, whereas the selector must read alphabetically.
  const primitiveTypes = useMemo(
    () => [...descriptorsByType.keys()].sort((a, b) => a.localeCompare(b)),
    [descriptorsByType],
  );

  return {
    descriptorsByType,
    primitiveTypes,
    isLoading,
    error,
  };
};

export default usePrimitiveTypeDescriptors;

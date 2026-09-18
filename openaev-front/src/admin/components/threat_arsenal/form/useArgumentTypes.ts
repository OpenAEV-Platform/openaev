import { useMemo } from 'react';

import usePrimitiveTypeDescriptors from '../../chaining/logic/usePrimitiveTypeDescriptors';

type UseArgumentTypesResult = {
  argumentTypes: string[];
  argumentWithDefaultValueTypes: Set<string>;
  isLoading: boolean;
  error: Error | null;
};

/**
 * Primitive types offered as payload argument types.
 *
 * Reads the same backend descriptors as the chaining condition editor: both need the exhaustive
 * list of primitive types, so they share a single memoized fetch instead of two endpoints.
 */
const useArgumentTypes = (): UseArgumentTypesResult => {
  const { primitiveTypes, isLoading, error } = usePrimitiveTypeDescriptors();

  const argumentWithDefaultValueTypes = useMemo(
    () => new Set(primitiveTypes.filter(type => type !== 'targeted-asset')),
    [primitiveTypes],
  );

  return {
    argumentTypes: primitiveTypes,
    argumentWithDefaultValueTypes,
    isLoading,
    error,
  };
};

export default useArgumentTypes;

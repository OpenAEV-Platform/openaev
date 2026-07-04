import { useEffect, useMemo, useState } from 'react';

import fetchArgumentTypes from '../../../../actions/payloads/payload-argument-actions';
import { type PrimitiveTypeOutput } from '../../../../utils/api-types';

type UseArgumentTypesResult = {
  argumentTypes: PrimitiveTypeOutput[];
  argumentWithDefaultValueTypes: Set<string>;
  isLoading: boolean;
  error: Error | null;
};

const useArgumentTypes = (): UseArgumentTypesResult => {
  const [argumentTypes, setArgumentTypes] = useState<PrimitiveTypeOutput[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);

  useEffect(() => {
    const loadArgumentTypes = async () => {
      try {
        setIsLoading(true);
        setError(null);
        const data = await fetchArgumentTypes();
        setArgumentTypes(data);
      } catch (err) {
        setError(err instanceof Error ? err : new Error('Failed to fetch argument types'));
      } finally {
        setIsLoading(false);
      }
    };

    void loadArgumentTypes();
  }, []);

  const argumentWithDefaultValueTypes = useMemo(() => {
    return new Set(
      argumentTypes
        .map(argumentType => argumentType.argument_type)
        .filter(type => type !== 'targeted-asset'),
    );
  }, [argumentTypes]);

  return {
    argumentTypes,
    argumentWithDefaultValueTypes,
    isLoading,
    error,
  };
};

export default useArgumentTypes;

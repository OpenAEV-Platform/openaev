import { useEffect, useMemo, useState } from 'react';

import fetchArgumentTypes from '../../../../actions/payloads/payload-argument-actions';

type UseArgumentTypesResult = {
  argumentTypes: string[];
  argumentWithDefaultValueTypes: Set<string>;
  isLoading: boolean;
  error: Error | null;
};

let argumentTypesPromise: Promise<string[]> | null = null;

const getArgumentTypes = (): Promise<string[]> => {
  argumentTypesPromise ??= fetchArgumentTypes()
    .then(data => [...data].sort((a, b) => a.localeCompare(b)))
    .catch((error) => {
      argumentTypesPromise = null;
      throw error;
    });
  return argumentTypesPromise;
};

const useArgumentTypes = (): UseArgumentTypesResult => {
  const [argumentTypes, setArgumentTypes] = useState<string[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);

  useEffect(() => {
    const loadArgumentTypes = async () => {
      try {
        setIsLoading(true);
        setError(null);
        const data = await getArgumentTypes();
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
      argumentTypes.filter(type => type !== 'targeted-asset'),
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

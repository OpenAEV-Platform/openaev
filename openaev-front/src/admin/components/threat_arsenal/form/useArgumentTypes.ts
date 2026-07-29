import { useEffect, useMemo, useState } from 'react';

import { fetchArgumentTypes } from '../../../../actions/chaining/chaining-actions';

type UseArgumentTypesResult = {
  argumentTypes: string[];
  argumentWithDefaultValueTypes: Set<string>;
  isLoading: boolean;
  error: Error | null;
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
        const data = await fetchArgumentTypes();
        setArgumentTypes([...data].sort((a, b) => a.localeCompare(b)));
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

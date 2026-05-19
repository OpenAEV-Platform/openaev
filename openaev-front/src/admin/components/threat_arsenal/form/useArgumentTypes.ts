import { useEffect, useMemo, useState } from 'react';

import fetchArgumentTypes from '../../../../actions/payloads/payload-argument-actions';
import { type ArgumentTypeOutput } from '../../../../utils/api-types';

type UseArgumentTypesResult = {
  argumentTypes: ArgumentTypeOutput[];
  subtypesByType: Record<string, NonNullable<ArgumentTypeOutput['argument_subtypes']>>;
  structuredTypes: Set<ArgumentTypeOutput['argument_type']>;
  argumentWithDefaultValueTypes: Set<string>;
  isLoading: boolean;
  error: Error | null;
};

const useArgumentTypes = (): UseArgumentTypesResult => {
  const [argumentTypes, setArgumentTypes] = useState<ArgumentTypeOutput[]>([]);
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

  const subtypesByType = useMemo(() => {
    return argumentTypes.reduce<
      Record<string, NonNullable<ArgumentTypeOutput['argument_subtypes']>>
    >((acc, argumentType) => {
      const subTypes = argumentType.argument_subtypes ?? [];
      if (subTypes.length > 0) {
        acc[argumentType.argument_type] = subTypes;
      }
      return acc;
    }, {});
  }, [argumentTypes]);

  const structuredTypes = useMemo(() => {
    return new Set(
      argumentTypes
        .filter(argumentType => (argumentType.argument_subtypes ?? []).length > 0)
        .map(argumentType => argumentType.argument_type),
    );
  }, [argumentTypes]);

  const argumentWithDefaultValueTypes = useMemo(() => {
    return new Set(
      argumentTypes
        .map(argumentType => argumentType.argument_type)
        .filter(type => type !== 'targeted-asset'),
    );
  }, [argumentTypes]);

  return {
    argumentTypes,
    subtypesByType,
    structuredTypes,
    argumentWithDefaultValueTypes,
    isLoading,
    error,
  };
};

export default useArgumentTypes;

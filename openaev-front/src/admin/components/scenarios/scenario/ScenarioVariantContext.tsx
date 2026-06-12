import { createContext, type FunctionComponent, type ReactNode, useCallback, useContext, useEffect, useState } from 'react';
import { useParams } from 'react-router';

import { simpleCall, simpleDelCall, simplePostCall, simplePutCall } from '../../../../utils/Action';

export interface ScenarioVariantConfig {
  scope_allow_list_label?: string;
  condition_sub_filter_enabled?: boolean;
}

export interface ScenarioVariant {
  variant_id: string;
  variant_name: string;
  variant_scenario_id: string;
  variant_config: ScenarioVariantConfig;
  variant_is_active: boolean;
  variant_created_at?: string;
}

interface ScenarioVariantContextType {
  variants: ScenarioVariant[];
  activeVariant: ScenarioVariant | null;
  loading: boolean;
  switchVariant: (variantId: string) => Promise<void>;
  createVariant: (name: string, config?: ScenarioVariantConfig) => Promise<void>;
  deleteVariant: (variantId: string) => Promise<void>;
}

export const ScenarioVariantContext = createContext<ScenarioVariantContextType>({
  variants: [],
  activeVariant: null,
  loading: false,
  switchVariant: async () => {},
  createVariant: async () => {},
  deleteVariant: async () => {},
});

export const useScenarioVariant = () => useContext(ScenarioVariantContext);

interface Props {
  children: ReactNode;
}

export const ScenarioVariantProvider: FunctionComponent<Props> = ({ children }) => {
  const { scenarioId } = useParams() as { scenarioId: string };
  const [variants, setVariants] = useState<ScenarioVariant[]>([]);
  const [loading, setLoading] = useState(false);

  const fetchVariants = useCallback(async () => {
    if (!scenarioId) return;
    setLoading(true);
    try {
      const result = await simpleCall(`/api/scenarios/${scenarioId}/variants`);
      setVariants((result.data as ScenarioVariant[]) ?? []);
    } catch {
      setVariants([]);
    } finally {
      setLoading(false);
    }
  }, [scenarioId]);

  useEffect(() => {
    fetchVariants();
  }, [fetchVariants]);

  const activeVariant = variants.find(v => v.variant_is_active) ?? null;

  const switchVariant = useCallback(async (variantId: string) => {
    await simplePutCall(`/api/scenarios/${scenarioId}/variants/${variantId}/activate`, {}, undefined, true, false);
    await fetchVariants();
  }, [scenarioId, fetchVariants]);

  const createVariant = useCallback(async (name: string, config?: ScenarioVariantConfig) => {
    await simplePostCall(`/api/scenarios/${scenarioId}/variants`, { variant_name: name, variant_config: config ?? {} }, undefined, true, false);
    await fetchVariants();
  }, [scenarioId, fetchVariants]);

  const deleteVariant = useCallback(async (variantId: string) => {
    await simpleDelCall(`/api/scenarios/${scenarioId}/variants/${variantId}`, undefined, true, false);
    await fetchVariants();
  }, [scenarioId, fetchVariants]);

  return (
    <ScenarioVariantContext.Provider value={{ variants, activeVariant, loading, switchVariant, createVariant, deleteVariant }}>
      {children}
    </ScenarioVariantContext.Provider>
  );
};

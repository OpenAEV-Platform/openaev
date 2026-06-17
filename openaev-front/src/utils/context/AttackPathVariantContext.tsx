import { createContext, type FunctionComponent, type ReactNode, useContext, useState } from 'react';

export type AttackPathVariantType = 'action' | 'endpoint' | 'v1' | 'v2' | 'v3' | 'v4' | 'v4u' | 'v4u2' | 'v4u3' | 'v4u4' | 'v4u5' | 'v5' | 'v6';

export interface AttackPathVariant {
  variant_id: string;
  variant_name: string;
  variant_type: AttackPathVariantType;
  variant_description?: string;
}

interface AttackPathVariantContextValue {
  variants: AttackPathVariant[];
  selectedVariantId: string | null;
  setVariants: (variants: AttackPathVariant[]) => void;
  setSelectedVariantId: (id: string | null) => void;
}

export const AttackPathVariantContext = createContext<AttackPathVariantContextValue>({
  variants: [],
  selectedVariantId: null,
  setVariants: () => {},
  setSelectedVariantId: () => {},
});

export const AttackPathVariantProvider: FunctionComponent<{ children: ReactNode }> = ({ children }) => {
  const [variants, setVariants] = useState<AttackPathVariant[]>([]);
  const [selectedVariantId, setSelectedVariantId] = useState<string | null>(null);

  return (
    <AttackPathVariantContext.Provider value={{ variants, selectedVariantId, setVariants, setSelectedVariantId }}>
      {children}
    </AttackPathVariantContext.Provider>
  );
};

export const useAttackPathVariant = () => useContext(AttackPathVariantContext);

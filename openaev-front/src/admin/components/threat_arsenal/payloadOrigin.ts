import { type PayloadSimple } from '../../../utils/api-types';

export type PayloadOrigin = 'CATALOG' | 'CUSTOMIZED' | 'LOCAL';

export const PAYLOAD_ORIGIN_LABELS: Record<PayloadOrigin, string> = {
  CATALOG: 'Filigran',
  CUSTOMIZED: 'Customized',
  LOCAL: 'Local',
};

// Where a payload version comes from: Filigran's catalogue, a customization of it, or this platform.
export const payloadOrigin = (payload?: PayloadSimple): PayloadOrigin | null => {
  if (!payload) return null;
  if (payload.payload_variant) return 'CUSTOMIZED';
  return payload.payload_managed ? 'CATALOG' : 'LOCAL';
};

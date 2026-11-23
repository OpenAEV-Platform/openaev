import { type FactOutput } from '../../utils/api-types';

// The fact keys OpenCRQ answers (xtm.lookup object_facts), in display order, with their labels:
// the risk scenarios the object drives, their annual loss expectancy and, for a CVE, the loss
// avoided by fixing it. Amounts are whole units of the OpenCRQ tenant's currency.
export const FACT_LABELS: Record<string, string> = {
  risk_scenarios: 'Risk scenarios',
  annual_loss: 'Annual loss expectancy',
  fix_value: 'Loss avoided by fixing',
};
export const MONEY_FACTS = new Set(['annual_loss', 'fix_value']);

// The fact keys OpenAEV reads on an OpenCTI counterpart (OpenCTICounterpartProvider), in display
// order: how many reports mention it, its confidence, its last change and its markings.
export const OPENCTI_FACT_LABELS: Record<string, string> = {
  reports: 'Reports',
  confidence: 'Confidence',
  last_modified: 'Last modified',
  markings: 'Markings',
};
export const DATE_FACTS = new Set(['last_modified']);

// The fact keys XTM One answers for an inventory asset (XtmOneCounterpartProvider), in display order.
export const XTM_ONE_FACT_LABELS: Record<string, string> = {
  criticality: 'Criticality',
  category: 'Category',
};
// XTM One answers these as lower-case enums (very_high, generic_asset): they render with OpenAEV's
// own labels (AssetCriticality, AssetCategory: "Very high", "Host"), never as the raw value.
export const ENUM_FACTS = new Set(['criticality', 'category']);

/** The facts answered for the given labels, known keys with a value only, in display order. */
export const orderedFacts = (facts: FactOutput[] | undefined, labels: Record<string, string> = FACT_LABELS): FactOutput[] => Object.keys(labels)
  .map(key => (facts ?? []).find(f => f.fact_key === key && f.fact_value != null && f.fact_value !== ''))
  .filter((f): f is FactOutput => !!f);

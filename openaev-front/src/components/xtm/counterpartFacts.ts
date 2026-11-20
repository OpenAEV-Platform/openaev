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

/** The facts OpenCRQ answered, known keys with a value only, in display order. */
export const orderedFacts = (facts: FactOutput[] | undefined): FactOutput[] => Object.keys(FACT_LABELS)
  .map(key => (facts ?? []).find(f => f.fact_key === key && f.fact_value != null && f.fact_value !== ''))
  .filter((f): f is FactOutput => !!f);

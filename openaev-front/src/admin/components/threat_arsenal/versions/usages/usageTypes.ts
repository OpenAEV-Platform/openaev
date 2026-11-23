import { type PayloadUsageOutput } from '../../../../../utils/api-types';

interface UsageType {
  label: string;
  one: string;
  many: string;
}

export const USAGE_TYPES: Record<PayloadUsageOutput['usage_type'], UsageType> = {
  inject: {
    label: 'Inject',
    one: '1 scenario inject',
    many: '{count} scenario injects',
  },
  atomic_testing: {
    label: 'Atomic testing',
    one: '1 atomic test',
    many: '{count} atomic tests',
  },
  step: {
    label: 'Chained step',
    one: '1 chained step',
    many: '{count} chained steps',
  },
};

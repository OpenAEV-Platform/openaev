import { zodResolver } from '@hookform/resolvers/zod';
import { type UseFormProps } from 'react-hook-form';
import { z } from 'zod';

import { zodImplement } from '../../../../../utils/Zod';
import { type ExpectationInputForm } from './Expectation';

export const infoMessage = (type: string, t: (key: string) => string) => {
  if (type === 'ARTICLE') {
    return t('This expectation is handled automatically by the platform and triggered when target reads the articles');
  }
  if (type === 'CHALLENGE') {
    return t('This expectation is handled automatically by the platform and triggered when the target completes the challenges');
  }
  return '';
};

export const formProps = (initialValues: ExpectationInputForm, t: (key: string) => string): UseFormProps<ExpectationInputForm> => ({
  mode: 'onTouched',
  resolver: zodResolver(zodImplement<ExpectationInputForm>().with({
    expectation_type: z.string(),
    expectation_name: z.string().min(1, { message: t('Should not be empty') }),
    expectation_description: z.string().optional(),
    expectation_score: z.coerce.number().min(1, t('Score must be greater than 0')).max(100, t('Score must be less than or equal to 100')),
    expectation_expectation_group: z.boolean(),
    expiration_time_days: z.coerce.number().min(0),
    expiration_time_hours: z.coerce.number().min(0),
    expiration_time_minutes: z.coerce.number().min(0),
  })),
  defaultValues: initialValues,
});

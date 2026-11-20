import { Button } from '@filigran/design-system';
import { useContext } from 'react';
import { useFormContext, useWatch } from 'react-hook-form';

import { useFormatter } from '../../../../components/i18n';
import { type ActionFormValues } from '../utils/actionFormValues';
import FiligranValuesContext from './FiligranValuesContext';
import sameValue from './sameValue';

interface Props { fields: (keyof ActionFormValues)[] }

// Shown on a customized copy whose fields differ from Filigran's.
const RevertToFiligran = ({ fields }: Props) => {
  const { t } = useFormatter();
  const filigranValues = useContext(FiligranValuesContext);
  const { control, setValue } = useFormContext<ActionFormValues>();
  const current = useWatch({
    control,
    name: fields,
  }) as unknown[];
  if (!filigranValues || fields.every((field, index) => sameValue(current[index], filigranValues[field]))) {
    return null;
  }
  return (
    <Button
      type="button"
      size="sm"
      priority="tertiary"
      onClick={() => fields.forEach(field => setValue(field, filigranValues[field], {
        shouldDirty: true,
        shouldValidate: true,
      }))}
    >
      {t('Revert to Filigran\'s')}
    </Button>
  );
};

export default RevertToFiligran;

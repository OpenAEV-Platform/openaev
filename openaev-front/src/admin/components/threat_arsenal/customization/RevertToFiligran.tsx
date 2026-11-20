import { Button } from '@filigran/design-system';
import { useContext } from 'react';
import { useFormContext, useWatch } from 'react-hook-form';

import { useFormatter } from '../../../../components/i18n';
import FiligranValuesContext from './FiligranValuesContext';
import sameValue from './sameValue';

// Form field names: the action form mixes the fields of every action type.
interface Props { fields: string[] }

// Shown on a customized copy whose fields differ from Filigran's.
const RevertToFiligran = ({ fields }: Props) => {
  const { t } = useFormatter();
  const filigranValues = useContext(FiligranValuesContext);
  const { control, setValue } = useFormContext();
  const current = useWatch({
    control,
    name: fields,
  }) as unknown[];
  const filigran = filigranValues as Record<string, unknown> | null;
  if (!filigran || fields.every((field, index) => sameValue(current[index], filigran[field]))) {
    return null;
  }
  return (
    <Button
      type="button"
      size="sm"
      priority="tertiary"
      onClick={() => fields.forEach(field => setValue(field, filigran[field], {
        shouldDirty: true,
        shouldValidate: true,
      }))}
    >
      {t('Revert to Filigran\'s')}
    </Button>
  );
};

export default RevertToFiligran;

import { Button } from '@filigran/design-system';
import { useContext } from 'react';
import { useFormContext, useWatch } from 'react-hook-form';

import { useFormatter } from '../../../../components/i18n';
import { type ThreatArsenalActionFormValues } from '../actionFormValues';
import VariantBaseContext from './VariantBaseContext';
import { sameValue } from './variantFields';

interface Props { fields: (keyof ThreatArsenalActionFormValues)[] }

const RevertToFiligran = ({ fields }: Props) => {
  const { t } = useFormatter();
  const base = useContext(VariantBaseContext);
  const { control, setValue } = useFormContext<ThreatArsenalActionFormValues>();
  const current = useWatch({
    control,
    name: fields,
  }) as unknown[];
  if (!base || fields.every((field, index) => sameValue(current[index], base[field]))) {
    return null;
  }
  return (
    <Button
      type="button"
      size="sm"
      priority="tertiary"
      onClick={() => fields.forEach(field => setValue(field, base[field], {
        shouldDirty: true,
        shouldValidate: true,
      }))}
    >
      {t('Revert to Filigran value')}
    </Button>
  );
};

export default RevertToFiligran;

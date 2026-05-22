import { zodResolver } from '@hookform/resolvers/zod';
import { useTheme } from '@mui/material/styles';
import { FormProvider, type SubmitHandler, useForm } from 'react-hook-form';
import { z } from 'zod';

import { type DomainHelper } from '../../../../../actions/domains/domain-helper';
import ActionButtons from '../../../../../components/common/ActionButtons';
import AttackPatternFieldController from '../../../../../components/fields/AttackPatternFieldController';
import DomainFieldController from '../../../../../components/fields/DomainFieldController';
import TagFieldController from '../../../../../components/fields/TagFieldController';
import { useFormatter } from '../../../../../components/i18n';
import { useHelper } from '../../../../../store';
import type { Domain } from '../../../../../utils/api-types';

export interface InjectorContractFormValues {
  injector_contract_attack_patterns?: string[];
  injector_contract_domains: string[];
  injector_contract_tags?: string[];
}

interface Props {
  onSubmit: SubmitHandler<InjectorContractFormValues>;
  handleClose: () => void;
  editing?: boolean;
  initialValues?: Partial<InjectorContractFormValues>;
}

const InjectorContractForm = ({
  onSubmit,
  handleClose,
  editing = false,
  initialValues = {
    injector_contract_attack_patterns: [],
    injector_contract_domains: [],
    injector_contract_tags: [],
  },
}: Props) => {
  const { t } = useFormatter();
  const theme = useTheme();

  const schema = z.object({
    injector_contract_attack_patterns: z.string().array().optional(),
    injector_contract_domains: z.string().array().refine(arr => arr.length > 0, t('This field is required.')),
    injector_contract_tags: z.string().array().optional(),
  });

  const methods = useForm<InjectorContractFormValues>({
    mode: 'onTouched',
    resolver: zodResolver(schema),
    defaultValues: initialValues,
  });

  const {
    handleSubmit,
    formState: { isDirty, isSubmitting },
  } = methods;

  const domainOptions: Domain[] = useHelper((helper: DomainHelper) => {
    return helper.getDomains();
  });

  return (
    <FormProvider {...methods}>
      <form
        style={{
          display: 'flex',
          flexDirection: 'column',
          minHeight: '100%',
          gap: theme.spacing(2),
        }}
        id="injectorContractForm"
        noValidate // disabled tooltip
        onSubmit={handleSubmit(onSubmit)}
      >
        <AttackPatternFieldController
          name="injector_contract_attack_patterns"
          label={t('Attack patterns')}
        />
        <DomainFieldController
          name="injector_contract_domains"
          label={t('Domains')}
          domains={domainOptions}
          required
        />
        <TagFieldController
          name="injector_contract_tags"
          label={t('Tags')}
        />
        <ActionButtons
          onCancel={handleClose}
          submitLabel={editing ? t('Update') : t('Create')}
          cancelLabel={t('Cancel')}
          disabled={!isDirty}
          submitting={isSubmitting}
          style={{
            flexDirection: 'row',
            justifyContent: 'flex-end',
          }}
        />
      </form>
    </FormProvider>
  );
};

export default InjectorContractForm;

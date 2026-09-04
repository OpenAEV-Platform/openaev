import { zodResolver } from '@hookform/resolvers/zod';
import { FormHelperText } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useMemo } from 'react';
import { FormProvider, type SubmitErrorHandler, type SubmitHandler, useForm } from 'react-hook-form';
import { z } from 'zod';

import ActionButtons from '../../../../components/common/ActionButtons';
import type { TabsEntry } from '../../../../components/common/tabs/Tabs';
import Tabs from '../../../../components/common/tabs/Tabs';
import useTabs from '../../../../components/common/tabs/useTabs';
import TextFieldController from '../../../../components/fields/TextFieldController';
import { useFormatter } from '../../../../components/i18n';
import Loader from '../../../../components/Loader';
import type { RoleInput } from '../../../../utils/api-types';
import useCapabilities from '../../../../utils/hooks/useCapabilities';
import useCapabilityGrants from '../../../../utils/hooks/useCapabilityGrants';
import CapabilitiesTab from './CapabilitiesTab';
import { useRoleScope } from './RoleScopeContext';

interface Props {
  onSubmit: SubmitHandler<RoleInput>;
  onCancel: () => void;
  editing?: boolean;
  initialValues?: RoleInput;
}

const RoleForm: FunctionComponent<Props> = ({
  onSubmit,
  onCancel,
  editing,
  initialValues = {
    role_name: '',
    role_description: '',
    role_capabilities: [],
  },
}) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const { scope } = useRoleScope();
  const { capabilities, loading } = useCapabilities(scope);
  const { missingCapabilities } = useCapabilityGrants(capabilities);

  const schema = useMemo(
    () =>
      z.object({
        role_name: z.string().min(1, { message: t('Should not be empty') }),
        role_description: z.string().optional(),
        role_capabilities: z.string().array().superRefine((values, ctx) => {
          const restricted = missingCapabilities(values);
          if (restricted.length > 0) {
            ctx.addIssue({
              code: 'custom',
              message: t('The current user must remove the restricted capabilities before saving: {capabilities}',
                { capabilities: restricted.map(capability => t(capability)).join(', ') }),
            });
          }
        }),
      }),
    [t, missingCapabilities],
  );

  type FormInput = z.infer<typeof schema>;

  const methods = useForm<FormInput>({
    mode: 'onTouched',
    resolver: zodResolver(schema),
    defaultValues: initialValues,
  });

  const {
    formState: { errors, isDirty, isSubmitting },
    handleSubmit,
  } = methods;

  const tabEntries: TabsEntry[] = [
    {
      key: 'Overview',
      label: 'Overview',
    },
    {
      key: 'Capabilities',
      label: 'Capabilities',
    },
  ];
  const { currentTab, handleChangeTab } = useTabs(tabEntries[0].key);

  const fieldTabs: Record<keyof FormInput, string> = {
    role_name: 'Overview',
    role_description: 'Overview',
    role_capabilities: 'Capabilities',
  };

  const handleInvalidSubmit: SubmitErrorHandler<FormInput> = (validationErrors) => {
    const firstError = Object.keys(validationErrors)[0] as keyof FormInput;
    if (fieldTabs[firstError]) handleChangeTab(fieldTabs[firstError]);
  };

  return (
    <FormProvider {...methods}>
      <Tabs
        entries={tabEntries}
        currentTab={currentTab}
        onChange={newValue => handleChangeTab(newValue)}
      />
      <form
        onSubmit={handleSubmit(onSubmit as SubmitHandler<FormInput>, handleInvalidSubmit)}
        noValidate
        style={{
          display: 'flex',
          flexDirection: 'column',
          marginTop: currentTab === 'Overview' ? theme.spacing(2) : 0,
          gap: currentTab === 'Overview' ? theme.spacing(2) : 0,
        }}
      >
        {currentTab === 'Overview' && (
          <>
            <TextFieldController name="role_name" label={t('Name')} required />
            <TextFieldController name="role_description" label={t('Description')} multiline rows={3} />
          </>
        )}

        {currentTab === 'Capabilities' && (
          <>
            {loading
              ? <Loader />
              : capabilities.map(cap => (
                  <CapabilitiesTab<FormInput>
                    capability={cap}
                    key={cap.capability_value}
                    fieldName="role_capabilities"
                    capabilities={capabilities}
                  />
                ))}
            {errors.role_capabilities && (
              <FormHelperText error>
                {errors.role_capabilities.message}
              </FormHelperText>
            )}
          </>
        )}

        <div style={{
          marginTop: theme.spacing(2),
          alignSelf: 'flex-end',
        }}
        >
          <ActionButtons
            onCancel={onCancel}
            cancelLabel={t('Cancel')}
            submitLabel={editing ? t('Update') : t('Create')}
            disabled={!isDirty}
            submitting={isSubmitting}
          />
        </div>
      </form>
    </FormProvider>
  );
};

export default RoleForm;

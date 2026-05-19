import { zodResolver } from '@hookform/resolvers/zod';
import { useTheme } from '@mui/material/styles';
import { type FormEvent, type FunctionComponent, useMemo } from 'react';
import { FormProvider, type SubmitHandler, useForm } from 'react-hook-form';
import { z } from 'zod';

import ActionButtons from '../../../../../components/common/ActionButtons';
import type { TabsEntry } from '../../../../../components/common/tabs/Tabs';
import Tabs from '../../../../../components/common/tabs/Tabs';
import useTabs from '../../../../../components/common/tabs/useTabs';
import TextFieldController from '../../../../../components/fields/TextFieldController';
import { useFormatter } from '../../../../../components/i18n';
import type { PlatformRoleInput } from '../../../../../utils/api-types';
import useCapabilities from '../../../../../utils/hooks/useCapabilities';
import CapabilitiesTab from '../../../settings/roles/CapabilitiesTab';

interface Props {
  onSubmit: SubmitHandler<PlatformRoleInput>;
  onCancel: () => void;
  editing?: boolean;
  initialValues?: PlatformRoleInput;
}

export interface PlatformRoleFormInput {
  platform_role_name: string;
  platform_role_description?: string;
  platform_role_capabilities: string[];
}

const PlatformRoleForm: FunctionComponent<Props> = ({
  onSubmit,
  onCancel,
  editing,
  initialValues = {
    platform_role_name: '',
    platform_role_description: '',
    platform_role_capabilities: [],
  },
}) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const { capabilities, loading } = useCapabilities('PLATFORM');

  const schema = useMemo(
    () =>
      z.object({
        platform_role_name: z.string().min(1, { message: t('Should not be empty') }).describe('Overview-tab'),
        platform_role_description: z.string().optional().describe('Overview-tab'),
        platform_role_capabilities: z.string().array().describe('Capabilities-tab'),
      }),
    [t],
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

  const getTabForField = (field: string) =>
    (schema.shape as Record<string, z.ZodTypeAny>)[field]?.description?.replace('-tab', '');

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

  const handleSubmitWithTab = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const isValid = await methods.trigger();
    if (!isValid) {
      const firstErrorField = Object.keys(errors)[0];
      const tabName = getTabForField(firstErrorField);
      if (tabName) handleChangeTab(tabName);
    } else {
      await handleSubmit(onSubmit as SubmitHandler<FormInput>)(e);
    }
  };

  return (
    <FormProvider {...methods}>
      <Tabs
        entries={tabEntries}
        currentTab={currentTab}
        onChange={newValue => handleChangeTab(newValue)}
      />
      <form
        onSubmit={handleSubmitWithTab}
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
            <TextFieldController name="platform_role_name" label={t('Name')} required />
            <TextFieldController name="platform_role_description" label={t('Description')} multiline rows={3} />
          </>
        )}

        {currentTab === 'Capabilities' && (
          <>
            {loading
              ? <span>{t('Loading...')}</span>
              : capabilities.map(cap => (
                  <CapabilitiesTab<PlatformRoleFormInput>
                    capability={cap}
                    key={cap.capability_value}
                    fieldName="platform_role_capabilities"
                    capabilities={capabilities}
                  />
                ))}
            {errors.platform_role_capabilities && <span>{errors.platform_role_capabilities.message}</span>}
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

export default PlatformRoleForm;

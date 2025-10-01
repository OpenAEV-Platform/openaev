import { zodResolver } from '@hookform/resolvers/zod';
import { Button } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FC, type FormEvent } from 'react';
import { FormProvider, useForm } from 'react-hook-form';
import { z } from 'zod';

import type { TabsEntry } from '../../../../components/common/tabs/Tabs';
import Tabs from '../../../../components/common/tabs/Tabs';
import useTabs from '../../../../components/common/tabs/useTabs';
import TextFieldController from '../../../../components/fields/TextFieldController';
import { useFormatter } from '../../../../components/i18n';
import capabilities from './capabilities.json';
import CapabilitiesTab from './CapabilitiesTab';

export interface RoleCreateInput {
  role_name: string;
  role_description?: string;
  role_capabilities: string[];
}

interface RoleFormProps {
  onSubmit: (data: RoleCreateInput) => void;
  handleClose: () => void;
  initialValues?: Partial<RoleCreateInput>;
  editing: boolean;
}

const RoleForm: FC<RoleFormProps> = ({
  onSubmit,
  handleClose,
  editing,
  initialValues = {},
}) => {
  const { t } = useFormatter();
  const theme = useTheme();

  /* ---------- Zod schema ---------- */
  const schema = z.object({
    role_name: z.string().min(1, { message: t('Should not be empty') }).describe('Overview-tab'),
    role_description: z.string().optional().describe('Overview-tab'),
    role_capabilities: z.string().array().describe('Capabilities-tab'),
  });

  const methods = useForm<RoleCreateInput>({
    mode: 'onTouched',
    resolver: zodResolver(schema),
    defaultValues: {
      role_name: '',
      role_description: '',
      role_capabilities: [],
      ...initialValues, // override if edition
    },
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
      await handleSubmit(onSubmit)(e);
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
            <TextFieldController name="role_name" label={t('Name')} required />
            <TextFieldController name="role_description" label={t('Description')} multiline={true} rows={3} />
          </>

        )}

        {currentTab === 'Capabilities' && (
          <>
            {capabilities.map(cap => (
              <CapabilitiesTab capability={cap} key={cap.name} capabilities={capabilities} />
            ))}
            {errors.role_capabilities && <span>{errors.role_capabilities.message}</span>}
          </>
        )}

        <div style={{
          marginTop: theme.spacing(2),
          display: 'flex',
          flexDirection: 'row-reverse',
          gap: theme.spacing(1),
        }}
        >
          <Button
            variant="contained"
            color="secondary"
            type="submit"
            disabled={isSubmitting || !isDirty}
          >
            {editing ? t('Update') : t('Create')}
          </Button>
          <Button
            variant="contained"
            onClick={handleClose}
            disabled={isSubmitting}
          >
            {t('Cancel')}
          </Button>
        </div>
      </form>
    </FormProvider>
  );
};

export default RoleForm;

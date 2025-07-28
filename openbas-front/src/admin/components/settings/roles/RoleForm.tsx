import { zodResolver } from '@hookform/resolvers/zod';
import { Button, Tab, Tabs } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FC, type FormEvent, type SyntheticEvent, useState } from 'react';
import { FormProvider, useForm } from 'react-hook-form';
import { z } from 'zod';

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

  const tabs = [
    {
      key: 'Overview',
      label: 'Overview',
    },
    {
      key: 'Capabilities',
      label: 'Capabilities',
    },
  ];
  const [activeTab, setActiveTab] = useState(tabs[0].key);

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

  const handleTabChange = (_: SyntheticEvent, newValue: string) => setActiveTab(newValue);

  const handleSubmitWithTab = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const isValid = await methods.trigger();
    if (!isValid) {
      const firstErrorField = Object.keys(errors)[0];
      const tabName = getTabForField(firstErrorField);
      if (tabName) setActiveTab(tabName);
    } else {
      handleSubmit(onSubmit)(e);
    }
  };

  return (
    <FormProvider {...methods}>
      <form
        onSubmit={handleSubmitWithTab}
        noValidate
        style={{
          display: 'flex',
          flexDirection: 'column',
          minHeight: '100%',
          gap: activeTab === 'Overview' ? theme.spacing(2) : 0,
        }}
      >
        <Tabs value={activeTab} onChange={handleTabChange}>
          {tabs.map(t => <Tab key={t.key} label={t.label} value={t.key} />)}
        </Tabs>

        {activeTab === 'Overview' && (
          <>
            <TextFieldController name="role_name" label={t('Name')} required />
            <TextFieldController name="role_description" label={t('Description')} multiline={true} rows={3} />
          </>

        )}

        {activeTab === 'Capabilities' && (
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

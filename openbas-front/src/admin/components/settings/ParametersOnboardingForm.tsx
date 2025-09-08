import { zodResolver } from '@hookform/resolvers/zod';
import { Box, Button, Divider, Paper, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useEffect } from 'react';
import { FormProvider, useForm } from 'react-hook-form';
import { makeStyles } from 'tss-react/mui';
import { z } from 'zod';

import { fetchDefaultPlatformParameters } from '../../../actions/Application';
import SwitchFieldController from '../../../components/fields/SwitchFieldController';
import { useFormatter } from '../../../components/i18n';
import { type PlatformSettings, type SettingsOnboardingUpdateInput } from '../../../utils/api-types';
import { useAppDispatch } from '../../../utils/hooks';
import { zodImplement } from '../../../utils/Zod';

const useStyles = makeStyles()(theme => ({
  paper: {
    padding: theme.spacing(2),
    borderRadius: 4,
  },
  paperList: {
    padding: theme.spacing(2),
    borderRadius: 4,
  },
  marginBottom: { marginBottom: theme.spacing(3) },
}));

interface ParametersOnboardingFormForms {
  onSubmit: (data: SettingsOnboardingUpdateInput) => void;
  initialValues: SettingsOnboardingUpdateInput;
}

const ParametersOnboardingForm: FunctionComponent<ParametersOnboardingFormForms> = ({
  onSubmit,
  initialValues,
}) => {
  const { classes } = useStyles();
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const theme = useTheme();
  const methods = useForm<SettingsOnboardingUpdateInput>({
    mode: 'onTouched',
    resolver: zodResolver(
      zodImplement<SettingsOnboardingUpdateInput>().with({
        platform_onboarding_widget_enable: z.boolean().optional(),
        platform_onboarding_contextual_help_enable: z.boolean().optional(),
      }),
    ),
    defaultValues: initialValues,
  });
  const { handleSubmit, reset } = methods;
  useEffect(() => {
    const subscription = methods.watch(() => {
      const values = methods.getValues();
      onSubmit(values);
    });
    return () => subscription.unsubscribe();
  }, [methods, onSubmit]);

  const handleReset = async () => {
    dispatch(fetchDefaultPlatformParameters())
      .then((result: {
        result: string;
        entities: { defaultPlatformParameters: Record<string, PlatformSettings> };
      }) => {
        const item = result.entities.defaultPlatformParameters[result.result];
        if (!item) return;
        reset(item);
        handleSubmit(onSubmit)();
      });
  };

  return (
    <Paper variant="outlined" classes={{ root: classes.paperList }}>
      <FormProvider {...methods}>
        <form
          id="parametersForm"
          onSubmit={(e) => {
            e.preventDefault();
            e.stopPropagation();
          }}
          style={{
            display: 'flex',
            flexDirection: 'column',
            minHeight: '100%',
            gap: theme.spacing(2),
          }}
        >
          <Box ml={2} mr={2} display="flex" alignItems="center" justifyContent="space-between">
            <Typography gutterBottom variant="subtitle1">{t('onboarding_process')}</Typography>
            <Button type="button" variant="text" size="small" onClick={handleReset} sx={{ marginRight: theme.spacing(1) }}>{t('Reset')}</Button>
          </Box>
          <Box ml={2} mr={2} display="flex" alignItems="center" justifyContent="space-between">
            <Typography variant="body2">{t('onboarding_display_widget')}</Typography>
            <SwitchFieldController name="platform_onboarding_widget_enable" label="" />
          </Box>
          <Divider />
          <Box ml={2} mr={2}>
            <Box display="flex" alignItems="center" justifyContent="space-between">
              <Typography variant="body2">{t('onboarding_display_contextual_buttons')}</Typography>
              <SwitchFieldController name="platform_onboarding_contextual_help_enable" label="" />
            </Box>
          </Box>
          <Divider />
        </form>
      </FormProvider>
    </Paper>
  );
};

export default ParametersOnboardingForm;

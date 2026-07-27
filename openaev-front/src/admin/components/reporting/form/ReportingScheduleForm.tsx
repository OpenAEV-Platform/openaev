import { zodResolver } from '@hookform/resolvers/zod';
import { Box, Button } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, useMemo } from 'react';
import { FormProvider, type SubmitHandler, useForm } from 'react-hook-form';
import { z } from 'zod';

import { useFormatter } from '../../../../components/i18n';
import ReportingScheduleFields, { type ReportingScheduleFieldsValues } from './ReportingScheduleFields';
import { buildScheduleFieldsSchema, scheduleValuesFromSchedule, validateScheduleTime } from './reportingScheduleUtils';

interface Props {
  onSubmit: SubmitHandler<ReportingScheduleFieldsValues>;
  handleClose: () => void;
  initialValues?: ReportingScheduleFieldsValues;
  editing?: boolean;
}

/**
 * Standalone schedule form of the report detail page (add / edit drawers).
 * The creation wizard embeds the same fields as its last step instead.
 */
const ReportingScheduleForm: FunctionComponent<Props> = ({
  onSubmit,
  handleClose,
  initialValues = scheduleValuesFromSchedule(),
  editing = false,
}) => {
  const { t } = useFormatter();
  const theme = useTheme();

  const validationSchema = useMemo(
    () => z.object(buildScheduleFieldsSchema())
      .superRefine((values, ctx) => validateScheduleTime(values, ctx, t)),
    [],
  );

  const methods = useForm<ReportingScheduleFieldsValues>({
    mode: 'onTouched',
    resolver: zodResolver(validationSchema),
    defaultValues: initialValues,
  });

  const {
    handleSubmit,
    formState: { isSubmitting, isDirty },
  } = methods;

  return (
    <FormProvider {...methods}>
      <form
        id="reportingScheduleForm"
        style={{
          display: 'flex',
          flexDirection: 'column',
          gap: theme.spacing(2),
        }}
        onSubmit={handleSubmit(onSubmit)}
      >
        <ReportingScheduleFields showEnabledSwitch />
        <Box sx={{
          display: 'flex',
          justifyContent: 'flex-end',
          mt: 2,
        }}
        >
          <Button
            variant="outlined"
            color="primary"
            onClick={handleClose}
            sx={{ mr: 1 }}
            disabled={isSubmitting}
          >
            {t('Cancel')}
          </Button>
          <Button
            variant="contained"
            color="primary"
            type="submit"
            disabled={isSubmitting || (editing && !isDirty)}
          >
            {editing ? t('Update') : t('Create')}
          </Button>
        </Box>
      </form>
    </FormProvider>
  );
};

export default ReportingScheduleForm;

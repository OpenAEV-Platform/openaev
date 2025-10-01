import { zodResolver } from '@hookform/resolvers/zod';
import { TableViewOutlined } from '@mui/icons-material';
import { Autocomplete as MuiAutocomplete, Box, Button, MenuItem, TextField, Tooltip } from '@mui/material';
import { DateTimePicker } from '@mui/x-date-pickers';
import { InformationOutline } from 'mdi-material-ui';
import moment from 'moment-timezone';
import { type FunctionComponent, type SyntheticEvent, useContext, useEffect, useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { makeStyles } from 'tss-react/mui';
import { z } from 'zod';

import { searchMappers } from '../../../../actions/mapper/mapper-actions';
import { type Page } from '../../../../components/common/queryable/Page';
import { useFormatter } from '../../../../components/i18n';
import { type ImportMapper, type ImportMessage, type ImportTestSummary, type InjectsImportInput } from '../../../../utils/api-types';
import { zodImplement } from '../../../../utils/Zod';
import { InjectContext } from '../Context';

const useStyles = makeStyles()(() => ({
  container: {
    display: 'flex',
    flexDirection: 'column',
    gap: '16px',
  },
  buttons: {
    display: 'flex',
    justifyContent: 'right',
    gap: '8px',
    marginTop: '24px',
  },
  icon: {
    paddingTop: 4,
    display: 'inline-block',
  },
  text: {
    display: 'inline-block',
    flexGrow: 1,
    marginLeft: 10,
  },
}));

interface FormProps {
  sheetName: string;
  importMapperId: string;
  startDate?: string;
  timezone: string;
}

interface Props {
  sheets: string[];
  importId: string;
  handleClose: () => void;
  handleSubmit: (input: InjectsImportInput) => void;
}

const ImportUploaderInjectFromXlsInjects: FunctionComponent<Props> = ({
  sheets,
  importId,
  handleClose,
  handleSubmit,
}) => {
  // Standard hooks
  const { t } = useFormatter();
  const { classes } = useStyles();

  // TimeZone
  const timezones = moment.tz.names();

  // Launch Date
  const [needLaunchDate, setNeedLaunchDate] = useState<boolean>(false);
  const injectContext = useContext(InjectContext);

  // Form
  const {
    register,
    control,
    handleSubmit: handleSubmitForm,
    formState: { errors, isDirty, isSubmitting },
    getValues,
  } = useForm<FormProps>({
    mode: 'onTouched',
    resolver: zodResolver(
      zodImplement<FormProps>().with({
        sheetName: z.string().min(1, { message: t('Should not be empty') }),
        importMapperId: z.string().min(1, { message: t('Should not be empty') }),
        timezone: z.string().min(1, { message: t('Should not be empty') }),
        startDate: z.string().optional(),
      }).refine(data => !needLaunchDate || (needLaunchDate && data.startDate !== undefined), {
        message: t('Should not be empty'),
        path: ['startDate'],
      }),
    ),
    defaultValues: { timezone: moment.tz.guess() },
  });

  // Mapper
  const [mappers, setMappers] = useState<ImportMapper[]>([]);
  useEffect(() => {
    searchMappers({ size: 10 }).then((result: { data: Page<ImportMapper> }) => {
      const { data } = result;
      setMappers(data.content);
    });
  }, []);
  const mapperOptions = mappers.map(
    m => ({
      id: m.import_mapper_id,
      label: m.import_mapper_name,
    }),
  );

  const onSubmitImportInjects = (values: FormProps) => {
    const input: InjectsImportInput = {
      import_mapper_id: values.importMapperId,
      sheet_name: values.sheetName,
      timezone_offset: moment.tz(values.timezone).utcOffset(),
      launch_date: values.startDate,
    };
    handleSubmit(input);
  };

  const handleSubmitWithoutPropagation = (e: SyntheticEvent) => {
    e.preventDefault();
    e.stopPropagation();
    handleSubmitForm(onSubmitImportInjects)(e);
  };

  const checkNeedLaunchDate = () => {
    const formValues = getValues();
    if (formValues.importMapperId && formValues.sheetName && formValues.timezone) {
      setNeedLaunchDate(false);
      const input: InjectsImportInput = {
        import_mapper_id: formValues.importMapperId,
        sheet_name: formValues.sheetName,
        timezone_offset: moment.tz(formValues.timezone).utcOffset(),
      };
      injectContext.onDryImportInjectFromXls?.(importId, input).then((value: ImportTestSummary) => {
        const criticalMessages = value.import_message?.filter((importMessage: ImportMessage) => importMessage.message_level === 'CRITICAL');
        if (criticalMessages && criticalMessages?.filter((message) => {
          return message.message_code === 'ABSOLUTE_TIME_WITHOUT_START_DATE';
        }).length > 0) {
          setNeedLaunchDate(true);
        }
      });
    }
  };

  return (
    <form id="importUploadInjectForm" onSubmit={handleSubmitWithoutPropagation}>
      <div className={classes.container}>
        <Controller
          control={control}
          name="sheetName"
          render={({ field: { onChange } }) => (
            <MuiAutocomplete
              size="small"
              selectOnFocus
              autoHighlight
              clearOnBlur={false}
              clearOnEscape={false}
              options={sheets}
              onChange={(_, v) => {
                onChange(v);
                checkNeedLaunchDate();
              }}
              renderInput={params => (
                <TextField
                  {...params}
                  label="Sheet"
                  variant="standard"
                  fullWidth
                  error={!!errors.sheetName}
                  helperText={errors.sheetName?.message}
                  InputLabelProps={{ required: true }}
                />
              )}
            />
          )}
        />
        <Controller
          control={control}
          name="importMapperId"
          render={({ field: { onChange } }) => (
            <MuiAutocomplete
              size="small"
              selectOnFocus
              autoHighlight
              clearOnBlur={false}
              clearOnEscape={false}
              options={mapperOptions}
              onChange={(_, v) => {
                onChange(v?.id);
                checkNeedLaunchDate();
              }}
              renderOption={(props, option) => (
                <Box component="li" {...props} key={option.id}>
                  <div className={classes.icon}>
                    <TableViewOutlined color="primary" />
                  </div>
                  <div className={classes.text}>{option.label}</div>
                </Box>
              )}
              getOptionLabel={option => option.label}
              isOptionEqualToValue={(option, v) => option.id === v.id}
              renderInput={params => (
                <TextField
                  {...params}
                  label="Mapper"
                  variant="standard"
                  fullWidth
                  error={!!errors.importMapperId}
                  helperText={errors.importMapperId?.message}
                  InputLabelProps={{ required: true }}
                />
              )}
            />
          )}
        />
        {needLaunchDate
          && (
            <Controller
              control={control}
              name="startDate"
              render={({ field, fieldState }) => (
                <DateTimePicker
                  views={['year', 'month', 'day']}
                  value={field.value ? new Date(field.value) : null}
                  minDate={new Date(new Date().setUTCHours(0, 0, 0, 0))}
                  onChange={startDate => field.onChange(startDate?.toISOString())}
                  slotProps={{
                    textField: {
                      fullWidth: true,
                      error: !!fieldState.error,
                      helperText: fieldState.error && fieldState.error?.message,
                      label: (
                        <Box display="flex" alignItems="center">
                          {t('Start date')}
                          <Tooltip title={t('The imported file contains absolute dates (ex.: 9h30). A starting date must be provided for the Scenario to be build')}>
                            <InformationOutline
                              fontSize="small"
                              color="primary"
                              style={{
                                marginLeft: 4,
                                cursor: 'default',
                              }}
                            />
                          </Tooltip>
                        </Box>
                      ),
                    },
                  }}
                />
              )}
            />
          )}
        <Controller
          control={control}
          name="timezone"
          render={({ field }) => (
            <TextField
              select
              variant="standard"
              fullWidth
              value={field.value}
              label={t('Timezone')}
              error={!!errors.timezone}
              helperText={errors.timezone?.message}
              inputProps={register('timezone')}
            >
              {timezones.map(tz => (
                <MenuItem key={tz} value={tz}>{t(tz)}</MenuItem>
              ))}
            </TextField>
          )}
        />
      </div>
      <div className={classes.buttons}>
        <Button
          onClick={handleClose}
          disabled={isSubmitting}
        >
          {t('Cancel')}
        </Button>
        <Button
          color="secondary"
          type="submit"
          disabled={!isDirty || isSubmitting}
        >
          {t('Launch import')}
        </Button>
      </div>
    </form>
  );
};

export default ImportUploaderInjectFromXlsInjects;

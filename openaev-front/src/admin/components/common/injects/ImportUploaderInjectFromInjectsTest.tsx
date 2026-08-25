import {
  Combobox,
  ComboboxContent,
  ComboboxControls,
  ComboboxField,
  ComboboxHelperText,
  ComboboxInput,
  ComboboxLabel,
  ComboboxTrigger,
  Select,
  SelectContent,
  SelectHelperText,
  SelectItem,
  SelectLabel,
  SelectTrigger,
  SelectValue,
} from '@filigran/design-system';
import { zodResolver } from '@hookform/resolvers/zod';
import { Box, Button } from '@mui/material';
import moment from 'moment-timezone';
import { type FunctionComponent, type SyntheticEvent, useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { makeStyles } from 'tss-react/mui';
import { z } from 'zod';

import { testXlsFile } from '../../../../actions/mapper/mapper-actions';
import CodeBlock from '../../../../components/common/CodeBlock';
import { useFormatter } from '../../../../components/i18n';
import { type ImportMapperAddInput, type ImportTestSummary, type InjectsImportTestInput } from '../../../../utils/api-types';
import { zodImplement } from '../../../../utils/Zod';

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
}));

// These two field labels were already untranslated before the Combobox
// adoption (they were `label="Sheet"` / `label="Mapper"` on a MUI
// TextField). They are kept untranslated rather than given invented
// translations in nine locales; adding real keys is a separate task.
const SHEET_LABEL = 'Sheet';

interface FormProps {
  sheetName: string;
  timezone: string;
}

interface Props {
  importId: string;
  sheets: string[];
  importMapperValues: ImportMapperAddInput;
  handleClose: () => void;
}

const ImportUploaderInjectFromInjectsTest: FunctionComponent<Props> = ({
  importId,
  sheets,
  importMapperValues,
  handleClose,
}) => {
  // Standard hooks
  const { t } = useFormatter();
  const { classes } = useStyles();

  // TimeZone
  const timezones = moment.tz.names();

  // Form
  const {
    control,
    handleSubmit: handleSubmitForm,
    formState: { errors, isDirty, isSubmitting },
  } = useForm<FormProps>({
    mode: 'onTouched',
    resolver: zodResolver(
      zodImplement<FormProps>().with({
        sheetName: z.string().min(1, { message: t('Should not be empty') }),
        timezone: z.string().min(1, { message: t('Should not be empty') }),
      }),
    ),
    defaultValues: { timezone: moment.tz.guess() },
  });

  const [result, setResult] = useState<ImportTestSummary | undefined>(undefined);

  const onSubmitImportTest = (values: FormProps) => {
    const input: InjectsImportTestInput = {
      import_mapper: importMapperValues,
      sheet_name: values.sheetName,
      timezone_offset: moment.tz(values.timezone).utcOffset(),
    };
    testXlsFile(importId, input).then((rs: { data: ImportTestSummary }) => {
      const { data } = rs;
      setResult(data);
    });
  };

  const handleSubmitWithoutPropagation = (e: SyntheticEvent) => {
    e.preventDefault();
    e.stopPropagation();
    handleSubmitForm(onSubmitImportTest)(e);
  };

  const [sheet, setSheet] = useState<string | null>(null);

  return (
    <form id="importUploadInjectForm" onSubmit={handleSubmitWithoutPropagation}>
      <div className={classes.container}>
        <Controller
          control={control}
          name="sheetName"
          render={({ field: { onChange } }) => (
            <Combobox<string>
              options={sheets}
              // The MUI field was uncontrolled; the library Combobox is always
              // controlled, so the field's own selection lives in local state.
              value={sheet}
              onValueChange={(v) => {
                setSheet(v as string | null);
                onChange(v);
              }}
              required
              error={!!errors.sheetName}
            >
              <ComboboxLabel>{SHEET_LABEL}</ComboboxLabel>
              <ComboboxField>
                <ComboboxInput />
                <ComboboxControls>
                  <ComboboxTrigger />
                </ComboboxControls>
              </ComboboxField>
              <ComboboxContent />
              {errors.sheetName?.message
                ? <ComboboxHelperText>{errors.sheetName.message}</ComboboxHelperText>
                : null}
            </Combobox>
          )}
        />
        <Controller
          control={control}
          name="timezone"
          render={({ field }) => (
            <Select
              value={field.value ?? ''}
              onValueChange={field.onChange}
              name={field.name}
              error={!!errors.timezone}
            >
              <SelectLabel>{t('Timezone')}</SelectLabel>
              <SelectTrigger>
                <SelectValue placeholder={t('Timezone')} />
              </SelectTrigger>
              <SelectContent>
                {timezones.map(tz => (
                  <SelectItem key={tz} value={tz}>{t(tz)}</SelectItem>
                ))}
              </SelectContent>
              {errors.timezone?.message ? <SelectHelperText>{errors.timezone?.message}</SelectHelperText> : null}
            </Select>
          )}
        />
      </div>
      <Box sx={{ marginTop: '8px' }}>
        <span>
          {t('Result')}
          {' '}
          :
          {' '}
        </span>
        <CodeBlock
          code={JSON.stringify(result?.injects, null, ' ') || t('You will find here the result in JSON format.')}
          language="json"
          maxHeight="250px"
        />
      </Box>
      <Box sx={{ marginTop: '8px' }}>
        <span>
          {t('Log')}
          {' '}
          :
          {' '}
        </span>
        <CodeBlock
          code={JSON.stringify(result?.import_message?.filter(i => i.message_level === 'ERROR' || i.message_level === 'CRITICAL'), null, ' ') || t('You will find here the result log in JSON format.')}
          language="json"
          maxHeight="200px"
        />
      </Box>
      <div className={classes.buttons}>
        <Button
          variant="outlined"
          color="primary"
          onClick={handleClose}
          disabled={isSubmitting}
        >
          {t('Cancel')}
        </Button>
        <Button
          variant="contained"
          color="primary"
          type="submit"
          disabled={!isDirty || isSubmitting}
        >
          {t('Test')}
        </Button>
      </div>
    </form>
  );
};

export default ImportUploaderInjectFromInjectsTest;

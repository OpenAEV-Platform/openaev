import { zodResolver } from '@hookform/resolvers/zod';
import { Button, Checkbox, FormControlLabel, TextField, Typography } from '@mui/material';
import { type FunctionComponent } from 'react';
import { type SubmitHandler, useForm } from 'react-hook-form';
import { z } from 'zod';

import { useFormatter } from '../../../../../components/i18n';
import { type Report, type ReportInformationInput, type ReportInput } from '../../../../../utils/api-types';
import { zodImplement } from '../../../../../utils/Zod';
import ReportInformationType from './ReportInformationType';

interface ExerciseReportFormInput {
  report_name: string;
  report_main_information: boolean;
  report_score_details: boolean;
  report_inject_result: boolean;
  report_global_observation: boolean;
  report_player_surveys: boolean;
  report_exercise_details: boolean;
}

interface ExerciseReportModulesConfig {
  type: ReportInformationType;
  name: 'report_main_information' | 'report_score_details' | 'report_inject_result' | 'report_player_surveys' | 'report_exercise_details' | 'report_name' | 'report_global_observation';
  label: string;
}

const exerciseReportModulesConfig: ExerciseReportModulesConfig[] = [
  {
    type: ReportInformationType.MAIN_INFORMATION,
    name: 'report_main_information',
    label: 'General information',
  },
  {
    type: ReportInformationType.SCORE_DETAILS,
    name: 'report_score_details',
    label: 'Score details',
  },
  {
    type: ReportInformationType.INJECT_RESULT,
    name: 'report_inject_result',
    label: 'Injects results',
  },
  {
    type: ReportInformationType.PLAYER_SURVEYS,
    name: 'report_player_surveys',
    label: 'Player surveys',
  },
  {
    type: ReportInformationType.GLOBAL_OBSERVATION,
    name: 'report_global_observation',
    label: 'Global observation',
  },
  {
    type: ReportInformationType.EXERCISE_DETAILS,
    name: 'report_exercise_details',
    label: 'Exercise details',
  },
];

interface Props {
  onSubmit: SubmitHandler<ReportInput>;
  handleCancel: () => void;
  editing?: boolean;
  initialValues?: Report;
}
const ExerciseReportForm: FunctionComponent<Props> = ({
  onSubmit,
  handleCancel,
  editing,
  initialValues,
}) => {
  // Standard hooks
  const { t } = useFormatter();

  const findReportInfo = (type: string) => {
    return initialValues?.report_informations?.find(info => info.report_informations_type === type)?.report_informations_display ?? true;
  };

  const initialModulesValues: Record<string, boolean> = {};
  exerciseReportModulesConfig.forEach((moduleConfig) => {
    initialModulesValues[moduleConfig.name] = findReportInfo(moduleConfig.type);
  });

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<ExerciseReportFormInput>({
    mode: 'onTouched',
    resolver: zodResolver(
      zodImplement<ExerciseReportFormInput>().with({
        report_name: z.string().min(1, { message: t('Should not be empty') }),
        report_exercise_details: z.boolean(),
        report_global_observation: z.boolean(),
        report_inject_result: z.boolean(),
        report_main_information: z.boolean(),
        report_player_surveys: z.boolean(),
        report_score_details: z.boolean(),
      })),
    defaultValues: {
      report_name: initialValues?.report_name || '',
      ...initialModulesValues,
    },
  });

  const onSubmitHandler = (data: ExerciseReportFormInput) => {
    const reportInformationList: ReportInformationInput[] = exerciseReportModulesConfig.map((moduleConfig) => {
      return {
        report_informations_type: moduleConfig.type,
        report_informations_display: !!data[moduleConfig.name],
      };
    });
    onSubmit({
      ...initialValues,
      report_name: data.report_name,
      report_informations: reportInformationList,
    });
  };

  return (
    <form
      id="reportExerciseForm"
      onSubmit={handleSubmit(onSubmitHandler)}
      style={{
        display: 'grid',
        gridTemplateColumns: '1fr 1fr',
      }}
    >
      <TextField
        variant="standard"
        sx={{ gridColumn: 'span 2' }}
        fullWidth={true}
        label={t('Name')}
        error={!!errors.report_name}
        helperText={errors.report_name && errors.report_name?.message}
        inputProps={register('report_name')}
      />
      <Typography
        variant="h4"
        gutterBottom
        sx={{
          gridColumn: 'span 2',
          marginTop: '30px',
        }}
      >
        {t('Modules')}
      </Typography>
      { exerciseReportModulesConfig.map((moduleConfig) => {
        return (
          <FormControlLabel
            key={moduleConfig.name}
            control={(
              <Checkbox
                {...register(moduleConfig.name)}
                defaultChecked={initialModulesValues[moduleConfig.name]}
              />
            )}
            label={t(moduleConfig.label)}
          />
        );
      })}
      <div style={{
        gridColumn: 'span 2',
        marginTop: '20px',
        display: 'flex',
      }}
      >
        <Button
          style={{ marginLeft: 'auto' }}
          onClick={handleCancel}
          disabled={isSubmitting}
        >
          {t('Cancel')}
        </Button>
        <Button
          color="secondary"
          type="submit"
          disabled={isSubmitting}
        >
          {editing ? t('Update') : t('Create')}
        </Button>
      </div>
    </form>
  );
};

export default ExerciseReportForm;

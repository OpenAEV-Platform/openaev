import { zodResolver } from '@hookform/resolvers/zod';
import { Button, TextField as MuiTextField, Typography } from '@mui/material';
import { type FunctionComponent, useContext } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { makeStyles } from 'tss-react/mui';
import { z } from 'zod';

import { type SecurityPlatformHelper } from '../../../../../../actions/assets/asset-helper';
import { fetchSecurityPlatforms } from '../../../../../../actions/assets/securityPlatform-actions';
import { updateInjectExpectation } from '../../../../../../actions/Exercise';
import ExpandableText from '../../../../../../components/common/ExpendableText';
import SecurityPlatformField from '../../../../../../components/fields/SecurityPlatformField';
import { useFormatter } from '../../../../../../components/i18n';
import ItemStatus from '../../../../../../components/ItemStatus';
import { useHelper } from '../../../../../../store';
import { type InjectExpectationResult, type SecurityPlatform } from '../../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../../utils/hooks';
import useDataLoader from '../../../../../../utils/hooks/useDataLoader';
import { AbilityContext, Can } from '../../../../../../utils/permissions/PermissionsProvider';
import RestrictionAccess from '../../../../../../utils/permissions/RestrictionAccess';
import { ACTIONS, SUBJECTS } from '../../../../../../utils/permissions/types';
import { zodImplement } from '../../../../../../utils/Zod';
import { type InjectExpectationsStore } from '../../../../common/injects/expectations/Expectation';

const useStyles = makeStyles()(theme => ({
  marginTop_2: { marginTop: theme.spacing(2) },
  buttons: {
    display: 'flex',
    placeContent: 'end',
    gap: theme.spacing(2),
    marginTop: theme.spacing(2),
  },
}));

interface FormProps {
  expectation: InjectExpectationsStore;
  result?: InjectExpectationResult;
  sourceIds?: string[];
  onUpdate?: () => void;
}

const DetectionPreventionExpectationsValidationForm: FunctionComponent<FormProps> = ({ expectation, result, sourceIds = [], onUpdate }) => {
  const { classes } = useStyles();
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const ability = useContext(AbilityContext);
  const { securityPlatformsMap }: { securityPlatformsMap: Record<string, SecurityPlatform> }
    = useHelper((helper: SecurityPlatformHelper) => ({ securityPlatformsMap: helper.getSecurityPlatformsMap() }));
  useDataLoader(() => {
    if (ability.can(ACTIONS.ACCESS, SUBJECTS.SECURITY_PLATFORMS)) dispatch(fetchSecurityPlatforms());
  });
  const onSubmit = (data: {
    expectation_score: number;
    security_platform: string;
  }) => {
    dispatch(updateInjectExpectation(expectation.inject_expectation_id, {
      ...data,
      source_id: data.security_platform,
      source_type: 'security-platform',
      source_platform: securityPlatformsMap[data.security_platform].security_platform_type,
      source_name: securityPlatformsMap[data.security_platform].asset_name,
    })).then(() => {
      onUpdate?.();
    });
  };
  const {
    register,
    control,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<{
    expectation_score: number;
    security_platform: string;
  }>({
    mode: 'onTouched',
    resolver: zodResolver(zodImplement<{
      expectation_score: number;
      security_platform: string;
    }>().with({
      expectation_score: z.coerce.number(),
      security_platform: z.string().min(1, { message: t('Should not be empty') }),
    })),
    defaultValues: {
      expectation_score: result?.score ?? expectation.inject_expectation_expected_score ?? 0,
      security_platform: result?.sourceId ?? '',
    },
  });

  // Security Platform Options
  const filterOptions = (n: SecurityPlatform) => (n.asset_external_reference === null && !sourceIds.includes(n.asset_id));

  return (
    <form id="expectationForm" onSubmit={handleSubmit(onSubmit)}>
      {result && (
        <div style={{ float: 'right' }}>
          <ItemStatus label={result?.result} status={result?.result} />
        </div>
      )}
      <Typography variant="h3">{t('Name')}</Typography>
      {expectation.inject_expectation_name}
      <div className={classes.marginTop_2}>
        <Typography variant="h3">{t('Description')}</Typography>
        <ExpandableText source={expectation.inject_expectation_description} limit={120} />
      </div>

      <Can not I={ACTIONS.ACCESS} a={SUBJECTS.SECURITY_PLATFORMS}>
        <RestrictionAccess restrictedField="security platforms" />
      </Can>

      <Controller
        control={control}
        name="security_platform"
        render={({ field: { onChange, value } }) => (
          <SecurityPlatformField
            name="security_platform"
            label={t('Security platform')}
            fieldValue={value ?? ''}
            fieldOnChange={onChange}
            errors={errors}
            filterOptions={filterOptions}
            style={{ marginTop: 20 }}
            editing={!!result}
          />
        )}
      />
      <MuiTextField
        className={classes.marginTop_2}
        variant="standard"
        fullWidth
        label={t('Score')}
        type="number"
        error={!!errors.expectation_score}
        helperText={errors.expectation_score && errors.expectation_score?.message ? errors.expectation_score?.message : `${t('Expected score:')} ${expectation.inject_expectation_expected_score}`}
        inputProps={register('expectation_score')}
      />

      <div className={classes.buttons}>
        <Button
          type="submit"
          disabled={isSubmitting}
          variant="contained"
        >
          {t('Validate')}
        </Button>
      </div>
    </form>
  );
};

export default DetectionPreventionExpectationsValidationForm;

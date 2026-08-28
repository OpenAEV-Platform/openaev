import { zodResolver } from '@hookform/resolvers/zod';
import { InfoOutlined } from '@mui/icons-material';
import { Box, TextField as MuiTextField, Typography } from '@mui/material';
import { type FunctionComponent, useContext } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { z } from 'zod';

import { type SecurityPlatformHelper } from '../../../../../../actions/assets/asset-helper';
import { fetchSecurityPlatforms } from '../../../../../../actions/assets/securityPlatform-actions';
import { updateInjectExpectation } from '../../../../../../actions/Exercise';
import SecurityPlatformField from '../../../../../../components/fields/SecurityPlatformField';
import { useFormatter } from '../../../../../../components/i18n';
import { useHelper } from '../../../../../../store';
import { type InjectExpectationResult, type SecurityPlatform } from '../../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../../utils/hooks';
import useDataLoader from '../../../../../../utils/hooks/useDataLoader';
import { AbilityContext, Can } from '../../../../../../utils/permissions/permissionsContext';
import RestrictionAccess from '../../../../../../utils/permissions/RestrictionAccess';
import { ACTIONS, SUBJECTS } from '../../../../../../utils/permissions/types';
import { zodImplement } from '../../../../../../utils/Zod';
import { type InjectExpectationsStore } from '../../../../common/injects/expectations/Expectation';
import { isAssetExpectation } from '../../../../common/injects/expectations/ExpectationUtils';

interface FormProps {
  expectation: InjectExpectationsStore;
  result?: InjectExpectationResult;
  sourceIds?: string[];
  onUpdate?: () => void;
}

// Fields-only form: the hosting dialog owns the header (name, type, status)
// and the actions bar, which submits through the "expectationForm" form id.
const DetectionPreventionExpectationsValidationForm: FunctionComponent<FormProps> = ({ expectation, result, sourceIds = [], onUpdate }) => {
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
    formState: { errors },
  } = useForm<{
    expectation_score: number;
    security_platform: string;
  }>({
    mode: 'onTouched',
    resolver: zodResolver(zodImplement<{
      expectation_score: number;
      security_platform: string;
    }>().with({
      expectation_score: z.number({ error: t('Score must be a valid number') }).min(0, { message: t('Should be superior or equal to 0') }).max(100, { message: t('Should be inferior or equal to 100') }).int(t('Score must be a whole number')),
      security_platform: z.string().min(1, { message: t('Should not be empty') }),
    })),
    defaultValues: {
      expectation_score: result?.score ?? expectation.inject_expectation_expected_score ?? 0,
      security_platform: result?.sourceId ?? '',
    },
  });

  // Security Platform Options. Only propose platforms whose type matches the
  // expectation's expected security platform types (EDR, SIEM, ...). An empty /
  // missing list means "any platform" (legacy behaviour), so we do not filter.
  const expectedPlatformTypes = expectation.inject_expectation_expected_security_platforms ?? [];
  const filterOptions = (n: SecurityPlatform) => (
    n.asset_external_reference === null
    && !sourceIds.includes(n.asset_id)
    && (expectedPlatformTypes.length === 0 || expectedPlatformTypes.includes(n.security_platform_type))
  );

  // Asset (endpoint) expectations aggregate their agents: a manual result added
  // here is written on every agent of the endpoint. Surface that so the behavior
  // is not surprising (mirrors the team -> players notice on manual expectations).
  const appliesToAllAgents = isAssetExpectation(expectation);

  return (
    <form id="expectationForm" onSubmit={handleSubmit(onSubmit)}>
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
            style={{}}
            editing={!!result}
          />
        )}
      />
      <MuiTextField
        variant="standard"
        fullWidth
        label={t('Score')}
        type="number"
        error={!!errors.expectation_score}
        helperText={errors.expectation_score?.message ?? `${t('Expected score:')} ${expectation.inject_expectation_expected_score}`}
        slotProps={{ htmlInput: { ...register('expectation_score', { valueAsNumber: true }) } }}
        sx={{ marginTop: 2.5 }}
      />

      {appliesToAllAgents && (
        <Box sx={{
          display: 'flex',
          alignItems: 'center',
          gap: 1,
          marginTop: 2.5,
        }}
        >
          <InfoOutlined sx={{
            fontSize: 16,
            color: 'text.secondary',
            flexShrink: 0,
          }}
          />
          <Typography sx={{
            fontSize: 12,
            color: 'text.secondary',
          }}
          >
            {t('The result added here will also be applied to all agents of this endpoint')}
          </Typography>
        </Box>
      )}
    </form>
  );
};

export default DetectionPreventionExpectationsValidationForm;

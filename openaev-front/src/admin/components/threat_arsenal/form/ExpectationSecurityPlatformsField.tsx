import { Box, FormControl, InputLabel, MenuItem, Select, Typography } from '@mui/material';
import { Controller, useFormContext, useWatch } from 'react-hook-form';

import { useFormatter } from '../../../../components/i18n';
import ItemSecurityPlatformType from '../../../../components/ItemSecurityPlatformType';
import { SECURITY_PLATFORM_TYPES } from '../../common/injects/expectations/Expectation';
import { isTechnicalExpectation } from '../../common/injects/expectations/ExpectationUtils';

// Canonical order + labels for the technical expectation types that can be
// scoped to specific security platforms (PREVENTION / DETECTION / VULNERABILITY).
// Human-validated types (MANUAL, ...) are fulfilled by people, not products, so
// they never carry a security-platform scope.
const TECHNICAL_EXPECTATION_LABELS: Record<string, string> = {
  PREVENTION: 'Prevention',
  DETECTION: 'Detection',
  VULNERABILITY: 'Vulnerability',
};

// Per-expectation "which security products should catch this" selector for the
// action form. For each selected TECHNICAL expectation it exposes a multi-select
// of security platform types; leaving one empty keeps the legacy "any security
// platform" behaviour. Bound to action_expected_security_platforms.<TYPE> so the
// value maps 1:1 to the API map.
const ExpectationSecurityPlatformsField = () => {
  const { t } = useFormatter();
  const { control } = useFormContext();

  const expectations = (useWatch({
    control,
    name: 'action_expectations',
  }) ?? []) as string[];

  const scopedTypes = expectations.filter(isTechnicalExpectation);
  if (scopedTypes.length === 0) {
    return null;
  }

  return (
    <Box sx={{
      display: 'flex',
      flexDirection: 'column',
      gap: 1,
    }}
    >
      <Box>
        <Typography variant="body2" sx={{ fontWeight: 600 }}>
          {t('Expected security platforms')}
        </Typography>
        <Typography variant="caption" sx={{ color: 'text.secondary' }}>
          {t('Scope each expectation to the security platform types expected to fulfil it. Leave empty for any security platform.')}
        </Typography>
      </Box>
      {scopedTypes.map((type) => {
        const fieldName = `action_expected_security_platforms.${type}`;
        const labelId = `expected-platforms-${type}`;
        return (
          <FormControl key={type} fullWidth>
            <InputLabel id={labelId} shrink>
              {t(TECHNICAL_EXPECTATION_LABELS[type] ?? type)}
            </InputLabel>
            <Controller
              name={fieldName}
              control={control}
              defaultValue={[]}
              render={({ field }) => (
                <Select
                  labelId={labelId}
                  multiple
                  displayEmpty
                  variant="standard"
                  fullWidth
                  value={(field.value as string[] | undefined) ?? []}
                  onChange={event => field.onChange(event.target.value)}
                  renderValue={(selected) => {
                    const values = selected as string[];
                    if (values.length === 0) {
                      return (
                        <Typography variant="body2" sx={{ color: 'text.secondary' }}>
                          {t('Any security platform')}
                        </Typography>
                      );
                    }
                    return (
                      <Box sx={{
                        display: 'flex',
                        flexWrap: 'wrap',
                        gap: 0.5,
                      }}
                      >
                        {values.map(platform => <ItemSecurityPlatformType key={platform} type={platform} />)}
                      </Box>
                    );
                  }}
                >
                  {SECURITY_PLATFORM_TYPES.map(platform => (
                    <MenuItem key={platform} value={platform}>
                      <ItemSecurityPlatformType type={platform} />
                    </MenuItem>
                  ))}
                </Select>
              )}
            />
          </FormControl>
        );
      })}
    </Box>
  );
};

export default ExpectationSecurityPlatformsField;

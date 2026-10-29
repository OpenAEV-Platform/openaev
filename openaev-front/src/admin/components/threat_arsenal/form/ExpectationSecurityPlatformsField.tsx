import {
  Combobox,
  ComboboxChips,
  ComboboxClear,
  ComboboxContent,
  ComboboxControls,
  ComboboxField,
  ComboboxInput,
  ComboboxLabel,
  ComboboxTrigger,
} from '@filigran/design-system';
import { Box, Typography } from '@mui/material';
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
        return (
          <div key={type}>
            <Controller
              name={fieldName}
              control={control}
              defaultValue={[]}
              render={({ field }) => (
                <Combobox<string>
                  multiple
                  options={SECURITY_PLATFORM_TYPES}
                  value={(field.value as string[] | undefined) ?? []}
                  onValueChange={next => field.onChange(next as string[])}
                  getOptionLabel={platform => platform}
                  isOptionEqualToValue={(a, b) => a === b}
                  renderOption={platform => <ItemSecurityPlatformType type={platform} />}
                  clearable={false}
                >
                  {/* The library field reserves no notch for a floating label,
                      so the label must be the library's own and sit above the
                      field. A MUI `InputLabel` here landed ON the field. */}
                  <ComboboxLabel>{t(TECHNICAL_EXPECTATION_LABELS[type] ?? type)}</ComboboxLabel>
                  <ComboboxField>
                    <ComboboxChips />
                    <ComboboxInput
                      name={field.name}
                      placeholder={t('Any security platform')}
                    />
                    <ComboboxControls>
                      <ComboboxClear />
                      <ComboboxTrigger />
                    </ComboboxControls>
                  </ComboboxField>
                  <ComboboxContent />
                </Combobox>
              )}
            />
          </div>
        );
      })}
    </Box>
  );
};

export default ExpectationSecurityPlatformsField;

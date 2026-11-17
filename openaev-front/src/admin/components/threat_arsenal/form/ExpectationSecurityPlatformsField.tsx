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
import { useEffect, useRef } from 'react';
import { Controller, useFormContext, useWatch } from 'react-hook-form';

import { useFormatter } from '../../../../components/i18n';
import ItemSecurityPlatformType from '../../../../components/ItemSecurityPlatformType';
import { ExpectationType, expectationTypeLabelKey, SECURITY_PLATFORM_TYPES } from '../../common/injects/expectations/Expectation';
import { isTechnicalExpectation } from '../../common/injects/expectations/ExpectationUtils';

const DETECTION_PLATFORMS = `action_expected_security_platforms.${ExpectationType.DETECTION}`;
const LOGGING_PLATFORMS = `action_expected_security_platforms.${ExpectationType.LOGGING}`;

const samePlatforms = (a: string[] | undefined, b: string[] | undefined) =>
  [...(a ?? [])].sort().join() === [...(b ?? [])].sort().join();

// Per-expectation "which security products should catch this" selector for the
// action form. For each selected TECHNICAL expectation it exposes a multi-select
// of security platform types; leaving one empty keeps the legacy "any security
// platform" behaviour. Bound to action_expected_security_platforms.<TYPE> so the
// value maps 1:1 to the API map.
const ExpectationSecurityPlatformsField = () => {
  const { t } = useFormatter();
  const { control, getValues, setValue } = useFormContext();

  const expectations = (useWatch({
    control,
    name: 'action_expectations',
  }) ?? []) as string[];
  const detectionPlatforms = useWatch({
    control,
    name: DETECTION_PLATFORMS,
  }) as string[] | undefined;

  // Logging's platforms default to Detection's: they follow Detection's until the user edits
  // Logging's own field. They follow from the start when both hold the same platforms (a new
  // payload), and again whenever Logging is added.
  const hasLogging = expectations.includes(ExpectationType.LOGGING);
  const hadLogging = useRef(hasLogging);
  const loggingFollowsDetection = useRef<boolean | null>(null);
  if (loggingFollowsDetection.current === null) {
    loggingFollowsDetection.current = samePlatforms(getValues(LOGGING_PLATFORMS), getValues(DETECTION_PLATFORMS));
  }
  useEffect(() => {
    if (hasLogging && !hadLogging.current) {
      loggingFollowsDetection.current = true;
    }
    hadLogging.current = hasLogging;
    if (hasLogging && loggingFollowsDetection.current && !samePlatforms(getValues(LOGGING_PLATFORMS), detectionPlatforms)) {
      setValue(LOGGING_PLATFORMS, [...(detectionPlatforms ?? [])], { shouldDirty: true });
    }
  }, [hasLogging, detectionPlatforms]);

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
                  onValueChange={(next) => {
                    if (type === ExpectationType.LOGGING) {
                      loggingFollowsDetection.current = false;
                    }
                    field.onChange(next as string[]);
                  }}
                  getOptionLabel={platform => platform}
                  isOptionEqualToValue={(a, b) => a === b}
                  renderOption={platform => <ItemSecurityPlatformType type={platform} />}
                  clearable={false}
                >
                  {/* The library field reserves no notch for a floating label,
                      so the label must be the library's own and sit above the
                      field. A MUI `InputLabel` here landed ON the field. */}
                  <ComboboxLabel>{t(expectationTypeLabelKey(type) ?? type)}</ComboboxLabel>
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

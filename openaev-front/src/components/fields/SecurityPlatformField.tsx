import { Box, TextField } from '@mui/material';
import {
  Combobox,
  ComboboxContent,
  ComboboxControls,
  ComboboxField,
  ComboboxInput,
  ComboboxLabel,
  ComboboxTrigger,
} from '@filigran/design-system';
import { useTheme } from '@mui/material/styles';
import { type CSSProperties, type FunctionComponent } from 'react';
import { type FieldErrors } from 'react-hook-form';
import { makeStyles } from 'tss-react/mui';

import { type SecurityPlatformHelper } from '../../actions/assets/asset-helper';
import { useHelper } from '../../store';
import { type SecurityPlatform } from '../../utils/api-types';
import { buildTenantApiPath } from '../../utils/url-helper';
import { securityPlatformTypeLabel } from '../securityPlatformType';

const useStyles = makeStyles()(() => ({
  icon: {
    paddingTop: 4,
    display: 'inline-block',
  },
  text: {
    display: 'inline-block',
    flexGrow: 1,
    marginLeft: 10,
  },
  autoCompleteIndicator: { display: 'none' },
}));

interface Props {
  name: string;
  label: string;
  fieldValue: string;
  fieldOnChange: (value: string) => void;
  errors: FieldErrors;
  filterOptions: (securityPlatform: SecurityPlatform) => boolean;
  style: CSSProperties;
  editing: boolean;
}

const securityPlatformsToOptions = (securityPlatforms: SecurityPlatform[], filterOptions: (securityPlatform: SecurityPlatform) => boolean) => {
  return securityPlatforms
    .filter(filterOptions)
    .map(n => ({
      id: n.asset_id,
      label: n.asset_name,
      logo_dark: n.security_platform_logo_dark,
      logo_light: n.security_platform_logo_light,
      type: n.security_platform_type,
    }));
};

const SecurityPlatformField: FunctionComponent<Props> = ({
  name,
  label,
  fieldValue,
  fieldOnChange,
  errors,
  filterOptions,
  style,
  editing,
}) => {
  // Standard hooks
  const theme = useTheme();
  const { classes } = useStyles();

  // Fetching data
  const { securityPlatforms }: { securityPlatforms: SecurityPlatform[] } = useHelper((helper: SecurityPlatformHelper) => ({ securityPlatforms: helper.getSecurityPlatforms() }));

  // Form
  const securityPlatformsOptions = securityPlatformsToOptions(securityPlatforms, filterOptions);

  const selectedValue = securityPlatformsOptions.find(option => option.id === fieldValue) || null;

  return (
    <div style={{ position: 'relative' }}>
      <Combobox
        options={securityPlatformsOptions}
        value={selectedValue}
        onValueChange={(value) => {
          fieldOnChange((value as { id: string } | null)?.id ?? '');
        }}
        getOptionLabel={option => option ? `${option.label} (${securityPlatformTypeLabel(option.type)})` : ''}
        isOptionEqualToValue={(option, value) => option.id === value.id}
        disabled={editing}
        error={!!errors[name]}
        // The MUI field hid its clear control via a `classes` override.
        clearable={false}
        renderOption={option => (
          <>
            <div className={classes.icon}>
              <img
                src={buildTenantApiPath(`/api/images/security_platforms/id/${option.id}/${theme.palette.mode}`)}
                alt={option.label}
                style={{
                  width: 25,
                  height: 25,
                  borderRadius: 4,
                }}
              />
            </div>
            <div className={classes.text}>
              {`${option.label} (${securityPlatformTypeLabel(option.type)})`}
            </div>
          </>
        )}
      >
        <ComboboxLabel>{label}</ComboboxLabel>
        <ComboboxField>
          <ComboboxInput />
          <ComboboxControls>
            <ComboboxTrigger />
          </ComboboxControls>
        </ComboboxField>
        <ComboboxContent />
      </Combobox>
    </div>
  );
};

export default SecurityPlatformField;

import { CSSProperties, FunctionComponent, useState } from 'react';
import type { GlobalError } from 'react-hook-form';
import { makeStyles } from 'tss-react/mui';
import { Autocomplete, Box, IconButton, TextField } from '@mui/material';
import { useFormatter } from '../i18n';
import { type TenantOutput, type TenantInput } from '../../utils/api-types';
import { useHelper } from '../../store';
import { TenantHelper } from '../../actions/helper';
import { useAppDispatch } from '../../utils/hooks';
import { Option } from '../../utils/Option';
import { addTenant, fetchTenants } from '../../actions/platform/tenants/tenant-action';
import { AddOutlined, DeviceHubOutlined } from '@mui/icons-material';
import Dialog from '../common/dialog/Dialog';
import TenantForm from '../../admin/components/platform/tenants/tenant/TenantForm';
import useDataLoader from '../../utils/hooks/useDataLoader';


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
  label?: string;
  style?: CSSProperties;
  fieldValue: string[];
  fieldOnChange: (values: string[]) => void;
  error?: GlobalError;
  disabled?: boolean;
  required?: boolean;
}

const TenantField: FunctionComponent<Props> = ({
  label,
  fieldValue,
  fieldOnChange,
  error,
  style = {},
  disabled = false,
  required = false,
}) => {

  // Standard hooks
  const { t } = useFormatter();
  const { classes } = useStyles();

  // Fetching data
  const { tenants }: { tenants: [TenantOutput] } = useHelper((helper: TenantHelper) => ({ tenants: helper.getTenants() }));
  const dispatch = useAppDispatch();
  useDataLoader(() => {
    dispatch(fetchTenants());
  });

  //Handle tenant creation
  const [tenantCreation, setTenantCreation] = useState(false);
  const handleOpenTenantCreation = () => setTenantCreation(true);
  const handleCloseTenantCreation = () => setTenantCreation(false);

  //Form
  const tenantOptions: Option[] = tenants.map(tenant => ({ id: tenant.tenant_id, label: tenant.tenant_name }));
  const values = () => {
    return tenantOptions.filter(option =>  (fieldValue ?? []).includes(option.id));
  }

  const handleSubmit = (data: TenantInput) => {
    dispatch(addTenant(data))
      .then((result: {
      result: string;
      entities: { tenants: Record<string, TenantOutput> };
    }) => {
      if (result.result) {
        fieldOnChange([...fieldValue, result.result]);
        handleCloseTenantCreation();
      }
      return result;
    });
  };

  return (
    // TODO: add CAN i for tenant creation (ACTIONS.MANAGE -submit button) and read (ACTIONS.SEARCH - liste déroulante)
    <div style={{
      position: 'relative',
      ...style,
    }}
    >
      <Autocomplete
        value={values()}
        size="small"
        multiple
        selectOnFocus
        autoHighlight
        disabled={disabled}
        clearOnBlur={false}
        clearOnEscape={false}
        options={tenantOptions}
        onChange={(_, value) => fieldOnChange(value.map(v => v.id))}
        isOptionEqualToValue={(option, value) => option.id === value.id}
        renderOption={(props, option) => (
          <Box component="li" {...props} key={option.id}>
            <div className={classes.icon}>
              <DeviceHubOutlined/>
            </div>
            <div className={classes.text}>
              {option.label}
            </div>
          </Box>
        )}
        renderInput={params => (
          <TextField
            {...params}
            label={label}
            variant="standard"
            fullWidth
            required={required}
            error={!!error}
            slotProps={{
              input: {
                ...params.InputProps,
                endAdornment: (
                  <>
                  <IconButton
                    style={{
                      position: 'absolute',
                      right: '35px',
                    }}
                    onClick={handleOpenTenantCreation}
                  >
                    <AddOutlined/>
                  </IconButton>
                  {params.InputProps.endAdornment}
                  </>
                ),
              },
            }}
          />)}
      />

      <Dialog
      open={tenantCreation}
      handleClose={handleCloseTenantCreation}
      title={t('Create a tenant')}
      >
        <TenantForm onSubmit={handleSubmit} onCancel={handleCloseTenantCreation} />
      </Dialog>

    </div>
      )

      };

      export default TenantField;

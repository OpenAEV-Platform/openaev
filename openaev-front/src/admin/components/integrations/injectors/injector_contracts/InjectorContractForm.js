import { Autocomplete, Box, Button, TextField } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import * as PropTypes from 'prop-types';
import { Field, Form } from 'react-final-form';

import { useFormatter } from '../../../../../components/i18n';
import OldAttackPatternField from '../../../../../components/OldAttackPatternField';
import { useHelper } from '../../../../../store';

const InjectorContractForm = (props) => {
  const { onSubmit, initialValues, editing, handleClose, isPayloadInjector } = props;

  const { t } = useFormatter();
  const theme = useTheme();

  const validate = (values) => {
    const errors = {};

    if (!Array.isArray(values.injector_contract_domains) || values.injector_contract_domains.length === 0) {
      errors.injector_contract_domains = t('This field is required.');
    }

    return errors;
  };

  const domainOptions = useHelper((helper) => {
    return helper.getDomains();
  });
  const filteredDomains = domainOptions.filter(d => d.domain_name !== 'To classify');
  return (
    <Form
      keepDirtyOnReinitialize={true}
      initialValues={initialValues}
      validate={validate}
      onSubmit={onSubmit}
      mutators={{
        setValue: ([field, value], state, { changeValue }) => {
          changeValue(state, field, () => value);
        },
      }}
    >
      {({ handleSubmit, form, values, submitting, pristine }) => (
        <form id="injectorContractForm" onSubmit={handleSubmit}>
          <OldAttackPatternField
            name="injector_contract_attack_patterns"
            label={t('Attack patterns')}
            values={values}
            setFieldValue={form.mutators.setValue}
            style={{ marginTop: theme.spacing(2) }}
          />
          {!isPayloadInjector && (
            <Field name="injector_contract_domains">
              {({ input, meta }) => {
                const safeValue = Array.isArray(input.value)
                  ? input.value
                      .map(id => filteredDomains.find(d => d.domain_id === id))
                      .filter(Boolean)
                  : [];

                return (
                  <Autocomplete
                    size="small"
                    multiple
                    options={filteredDomains}
                    getOptionLabel={option => option.domain_name || ''}
                    isOptionEqualToValue={(option, val) => option.domain_id === val.domain_id}
                    disableClearable={false}
                    openOnFocus
                    autoHighlight
                    noOptionsText="No available options"
                    value={safeValue}
                    onChange={(_event, selectedOptions) => {
                      input.onChange(selectedOptions.map(o => o.domain_id));
                    }}
                    renderInput={params => (
                      <TextField
                        {...params}
                        label={t('domains')}
                        variant="standard"
                        size="small"
                        fullWidth
                        error={meta.error && meta.touched}
                        helperText={meta.touched && meta.error ? meta.error : null}
                      />
                    )}
                    renderOption={(props, option) => (
                      <Box component="li" {...props} key={option.domain_id}>
                        {option.domain_name}
                      </Box>
                    )}
                  />
                );
              }}
            </Field>
          )}
          <div style={{
            float: 'right',
            marginTop: 20,
          }}
          >

            <Button
              variant="contained"
              onClick={handleClose}
              style={{ marginRight: 10 }}
              disabled={submitting}
            >
              {t('Cancel')}
            </Button>
            <Button
              color="secondary"
              type="submit"
              variant="contained"
              disabled={pristine || submitting}
            >
              {editing ? t('Update') : t('Create')}
            </Button>
          </div>
        </form>
      )}
    </Form>
  );
};

InjectorContractForm.propTypes = {
  onSubmit: PropTypes.func.isRequired,
  handleClose: PropTypes.func,
  editing: PropTypes.bool,
};

export default InjectorContractForm;

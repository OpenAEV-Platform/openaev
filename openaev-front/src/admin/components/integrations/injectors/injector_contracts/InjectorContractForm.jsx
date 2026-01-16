import { Button } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import * as PropTypes from 'prop-types';
import { Field, Form } from 'react-final-form';

import AutocompleteField from '../../../../../components/fields/AutocompleteField';
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
                const safeValue = (Array.isArray(input.value) ? input.value : [])
                  .map((val) => {
                    if (typeof val === 'string') {
                      return filteredDomains.find(d => d.domain_id === val);
                    }
                    return val;
                  })
                  .filter(Boolean);

                const mappedOptions = filteredDomains.map(d => ({
                  id: d.domain_id,
                  label: d.domain_name,
                }));

                const selectedIds = safeValue.map(d => d.domain_id);

                return (
                  <AutocompleteField
                    style={{ marginTop: theme.spacing(3) }}
                    label={t('Domains')}
                    variant="standard"
                    multiple
                    options={mappedOptions}
                    value={selectedIds}
                    onInputChange={() => { }}
                    error={meta.error && meta.touched}
                    onChange={(ids) => {
                      const selectedObjects = filteredDomains.filter(d =>
                        ids.includes(d.domain_id),
                      );
                      input.onChange(selectedObjects);
                    }}
                  />
                );
              }}
            </Field>
          )}

          <div style={{
            float: 'right',
            marginTop: theme.spacing(2),
          }}
          >

            <Button
              variant="contained"
              onClick={handleClose}
              style={{ marginRight: theme.spacing(2) }}
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

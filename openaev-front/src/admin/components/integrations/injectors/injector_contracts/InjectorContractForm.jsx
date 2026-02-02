import { Button } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import * as PropTypes from 'prop-types';
import { Field, Form } from 'react-final-form';

import DomainsAutocompleteField from '../../../../../components/DomainsAutocompleteField.tsx';
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
              {({ input, meta }) => (
                <DomainsAutocompleteField
                  input={input}
                  meta={meta}
                  domainOptions={domainOptions}
                  label={t('Domains')}
                  style={{ marginTop: theme.spacing(2) }}
                />
              )}
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

import { Button } from '@mui/material';
import * as PropTypes from 'prop-types';
import { Form } from 'react-final-form';

import OldTextField from '../../../../components/fields/OldTextField';
import { useFormatter } from '../../../../components/i18n';
import KillChainPhaseField from '../../../../components/KillChainPhaseField';

const AttackPatternForm = (props) => {
  const { onSubmit, initialValues, editing, handleClose } = props;
  const { t } = useFormatter();
  const validate = (values) => {
    const errors = {};
    const requiredFields = ['attack_pattern_external_id', 'attack_pattern_name'];
    requiredFields.forEach((field) => {
      if (!values[field]) {
        errors[field] = t('This field is required.');
      }
    });
    return errors;
  };
  return (
    <Form
      keepDirtyOnReinitialize
      initialValues={initialValues}
      onSubmit={onSubmit}
      validate={validate}
      mutators={{
        setValue: ([field, value], state, { changeValue }) => {
          changeValue(state, field, () => value);
        },
      }}
    >
      {({ handleSubmit, form, values, submitting, pristine }) => (
        <form id="attackPatternForm" onSubmit={handleSubmit}>
          <OldTextField
            name="attack_pattern_external_id"
            fullWidth
            label={t('External ID')}
            style={{ marginTop: 10 }}
          />
          <KillChainPhaseField
            name="attack_pattern_kill_chain_phases"
            label={t('Kill chain phases')}
            values={values}
            setFieldValue={form.mutators.setValue}
            style={{ marginTop: 20 }}
          />
          <OldTextField
            name="attack_pattern_name"
            fullWidth
            label={t('Name')}
            style={{ marginTop: 20 }}
          />
          <OldTextField
            name="attack_pattern_description"
            multiline
            fullWidth
            rows={3}
            label={t('Description')}
            style={{ marginTop: 20 }}
          />
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
              variant="contained"
              color="secondary"
              type="submit"
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

AttackPatternForm.propTypes = {
  onSubmit: PropTypes.func.isRequired,
  handleClose: PropTypes.func,
  editing: PropTypes.bool,
  initialValues: PropTypes.object,
};

export default AttackPatternForm;

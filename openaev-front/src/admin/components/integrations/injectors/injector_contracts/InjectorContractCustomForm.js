import { Button, GridLegacy, Switch, TextField as MUITextField, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import * as PropTypes from 'prop-types';
import * as R from 'ramda';
import { useState } from 'react';
import { Form } from 'react-final-form';

import CKEditor from '../../../../../components/CKEditor';
import OldTextField from '../../../../../components/fields/OldTextField';
import { useFormatter } from '../../../../../components/i18n';
import OldAttackPatternField from '../../../../../components/OldAttackPatternField';

const InjectorContractForm = (props) => {
  const { onSubmit, initialValues, editing, handleClose, contractTemplate } = props;
  const [fields, setFields] = useState({});
  const theme = useTheme();
  const { t } = useFormatter();
  const validate = (values) => {
    const errors = {};
    const requiredFields = ['injector_contract_name'];
    requiredFields.forEach((field) => {
      if (!values[field]) {
        errors[field] = t('This field is required.');
      }
    });
    return errors;
  };
  const contract = JSON.parse(contractTemplate.injector_contract_content);
  const renderField = (field) => {
    switch (field.type) {
      case 'textarea':
        return field.richText
          ? (
              <CKEditor
                data={!R.isNil(fields[field.key]?.defaultValue) ? fields[field.key].defaultValue : field.defaultValue}
                onChange={(_, editor) => {
                  setFields({
                    ...fields,
                    [field.key]: { defaultValue: editor.getData() },
                  });
                }}
              />
            )
          : (
              <MUITextField
                variant="standard"
                fullWidth={true}
                multiline={true}
                rows={10}
                style={{ marginTop: 5 }}
                value={!R.isNil(fields[field.key]?.defaultValue) ? fields[field.key].defaultValue : field.defaultValue}
                onChange={event => setFields({
                  ...fields,
                  [field.key]: { defaultValue: event.target.value },
                })}
              />
            );
      case 'number':
        return (
          <MUITextField
            variant="standard"
            fullWidth={true}
            type="number"
            style={{ marginTop: 5 }}
            value={!R.isNil(fields[field.key]?.defaultValue) ? fields[field.key].defaultValue : field.defaultValue}
            onChange={event => setFields({
              ...fields,
              [field.key]: { defaultValue: event.target.value },
            })}
          />
        );
      default:
        return (
          <MUITextField
            variant="standard"
            fullWidth={true}
            style={{ marginTop: 5 }}
            value={!R.isNil(fields[field.key]?.defaultValue) ? fields[field.key].defaultValue : field.defaultValue}
            onChange={event => setFields({
              ...fields,
              [field.key]: { defaultValue: event.target.value },
            })}
          />
        );
    }
  };
  return (
    <Form
      keepDirtyOnReinitialize={true}
      initialValues={initialValues}
      onSubmit={data => onSubmit(data, fields)}
      validate={validate}
      mutators={{
        setValue: ([field, value], state, { changeValue }) => {
          changeValue(state, field, () => value);
        },
      }}
    >
      {({ handleSubmit, form, values, submitting }) => (
        <form id="injectorContractCustomForm" onSubmit={handleSubmit}>
          <OldTextField
            name="injector_contract_name"
            fullWidth={true}
            label={t('Name')}
          />
          <OldAttackPatternField
            name="injector_contract_attack_patterns"
            label={t('Attack patterns')}
            values={values}
            setFieldValue={form.mutators.setValue}
            style={{ marginTop: 20 }}
            useExternalId={!editing}
          />
          {contract.fields.map((field) => {
            return (
              <div
                key={field.key}
                style={{
                  border: `1px solid ${theme.palette.action.hover}`,
                  padding: 10,
                  borderRadius: 4,
                  marginTop: 20,
                }}
              >
                <Typography
                  variant="h5"
                  gutterBottom={true}
                >
                  {field.label}
                </Typography>
                <GridLegacy container={true} spacing={3}>
                  <GridLegacy item={true} xs={6}>
                    <Typography
                      variant="h4"
                      gutterBottom={true}
                      style={{ marginTop: 20 }}
                    >
                      {t('Type')}
                    </Typography>
                    {field.type}
                  </GridLegacy>
                  <GridLegacy item={true} xs={6}>
                    <Typography
                      variant="h4"
                      gutterBottom={true}
                      style={{ marginTop: 20 }}
                    >
                      {t('Read only')}
                    </Typography>
                    <Switch
                      size="small"
                      checked={!R.isNil(fields[field.key]?.readOnly) ? fields[field.key].readOnly : field.readOnly}
                      onChange={event => setFields({
                        ...fields,
                        [field.key]: { readOnly: event.target.checked },
                      })}
                    />
                  </GridLegacy>
                </GridLegacy>
                <Typography
                  variant="h4"
                  gutterBottom={true}
                  style={{ marginTop: 10 }}
                >
                  {t('Default value')}
                </Typography>
                {renderField(field)}
              </div>
            );
          })}
          <div style={{
            float: 'right',
            marginTop: 20,
          }}
          >
            <Button
              onClick={handleClose}
              style={{ marginRight: 10 }}
              disabled={submitting}
              variant="contained"
            >
              {t('Cancel')}
            </Button>
            <Button
              color="secondary"
              type="submit"
              variant="contained"
              disabled={submitting}
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

import { InfoOutlined } from '@mui/icons-material';
import { Button, InputAdornment, Tooltip } from '@mui/material';
import { type FunctionComponent, useContext } from 'react';
import { Form } from 'react-final-form';
import { z } from 'zod';

import CountryField from '../../../../components/CountryField';
import OldTextField from '../../../../components/fields/OldTextField';
import { useFormatter } from '../../../../components/i18n';
import OrganizationField from '../../../../components/OrganizationField';
import TagField from '../../../../components/TagField';
import { AbilityContext } from '../../../../utils/permissions/PermissionsProvider';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import { schemaValidator } from '../../../../utils/Zod';
import { type PlayerInputForm } from './Player';

interface PlayerFormProps {
  initialValues: Partial<PlayerInputForm>;
  handleClose: () => void;
  onSubmit: (data: PlayerInputForm) => void;
  editing?: boolean;
}

const PlayerForm: FunctionComponent<PlayerFormProps> = ({
  editing,
  onSubmit,
  initialValues,
  handleClose,
}) => {
  // Standard hooks
  const { t } = useFormatter();
  const ability = useContext(AbilityContext);

  const playerFormSchemaValidation = z.object({
    user_email: z.string().email(t('Should be a valid email address')),
    user_phone: z
      .string()
      .regex(
        /^\+[\d\s\-.()]+$/,
        t('The country code and mobile phone number provided is invalid. Please provide a valid number'),
      )
      .optional()
      .nullable(),
    user_phone2: z
      .string()
      .regex(
        /^\+[\d\s\-.()]+$/,
        t('The country code and mobile phone number provided is invalid. Please provide a valid number'),
      )
      .optional()
      .nullable(),
  });
  return (
    <Form
      keepDirtyOnReinitialize
      initialValues={initialValues}
      onSubmit={onSubmit}
      validate={schemaValidator(playerFormSchemaValidation)}
      mutators={{
        setValue: ([field, value], state, { changeValue }) => {
          changeValue(state, field, () => value);
        },
      }}
    >
      {({ handleSubmit, form, values, submitting, pristine }) => (
        <form id="playerForm" onSubmit={handleSubmit}>
          <OldTextField
            variant="standard"
            name="user_email"
            fullWidth
            label={t('Email address')}
            disabled={editing}
          />
          <OldTextField
            variant="standard"
            name="user_firstname"
            fullWidth
            label={t('Firstname')}
            style={{ marginTop: 20 }}
          />
          <OldTextField
            variant="standard"
            name="user_lastname"
            fullWidth
            label={t('Lastname')}
            style={{ marginTop: 20 }}
          />
          <OrganizationField
            name="user_organization"
            values={values}
            setFieldValue={form.mutators.setValue}
            userRight={ability.can(ACTIONS.ACCESS, SUBJECTS.PLATFORM_SETTINGS)}
          />
          <CountryField
            name="user_country"
            values={values}
            setFieldValue={form.mutators.setValue}
          />
          <OldTextField
            InputProps={{
              endAdornment: (
                <InputAdornment position="start">
                  <Tooltip title={<span style={{ whiteSpace: 'pre-line' }}>{t('phone_number_tooltip')}</span>}>
                    <InfoOutlined />
                  </Tooltip>
                </InputAdornment>
              ),
            }}
            variant="standard"
            name="user_phone"
            fullWidth
            label={t('Phone number (mobile)')}
            style={{ marginTop: 20 }}
          />
          <OldTextField
            InputProps={{
              endAdornment: (
                <InputAdornment position="start">
                  <Tooltip title={<span style={{ whiteSpace: 'pre-line' }}>{t('phone_number_tooltip')}</span>}>
                    <InfoOutlined />
                  </Tooltip>
                </InputAdornment>
              ),
            }}
            variant="standard"
            name="user_phone2"
            fullWidth
            label={t('Phone number (landline)')}
            style={{ marginTop: 20 }}
          />
          <OldTextField
            variant="standard"
            name="user_pgp_key"
            fullWidth
            multiline
            rows={5}
            label={t('PGP public key')}
            style={{ marginTop: 20 }}
          />
          <TagField
            name="user_tags"
            label={t('Tags')}
            values={values}
            setFieldValue={form.mutators.setValue}
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

export default PlayerForm;

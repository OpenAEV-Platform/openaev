import { Icon, Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@filigran/design-system';
import { Button } from '@mui/material';
import { type FunctionComponent, useContext } from 'react';
import { Form } from 'react-final-form';
import { z } from 'zod';

import CountryField from '../../../../components/CountryField';
import OldTextField from '../../../../components/fields/OldTextField';
import { useFormatter } from '../../../../components/i18n';
import OrganizationField from '../../../../components/OrganizationField';
import TagField from '../../../../components/TagField';
import { AbilityContext } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import { PHONE_REGEX, schemaValidator } from '../../../../utils/Zod';
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
  const phoneTooltip = (
    <TooltipProvider delayDuration={200}>
      <Tooltip>
        <TooltipTrigger asChild>
          <span tabIndex={0} aria-label={t('Information')}><Icon name="info" size={16} aria-hidden /></span>
        </TooltipTrigger>
        <TooltipContent><span style={{ whiteSpace: 'pre-line' }}>{t('phone_number_tooltip')}</span></TooltipContent>
      </Tooltip>
    </TooltipProvider>
  );
  const ability = useContext(AbilityContext);

  const playerFormSchemaValidation = z.object({
    user_email: z.email(t('Should be a valid email address')),
    user_phone: z
      .string()
      .regex(
        PHONE_REGEX,
        t('The country code and mobile phone number provided is invalid. Please provide a valid number'),
      )
      .optional()
      .nullable(),
    user_phone2: z
      .string()
      .regex(
        PHONE_REGEX,
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
            name="user_email"
            label={t('Email address')}
            disabled={editing}
          />
          <OldTextField
            name="user_firstname"
            label={t('Firstname')}
            style={{ marginTop: 20 }}
          />
          <OldTextField
            name="user_lastname"
            label={t('Lastname')}
            style={{ marginTop: 20 }}
          />
          <OrganizationField
            name="user_organization"
            values={values}
            setFieldValue={form.mutators.setValue}
            userRight={ability.can(ACTIONS.ACCESS, SUBJECTS.TENANT_SETTINGS)}
          />
          <CountryField
            name="user_country"
            values={values}
            setFieldValue={form.mutators.setValue}
          />
          <OldTextField
            infoTooltip={phoneTooltip}
            name="user_phone"
            label={t('Phone number (mobile)')}
            style={{ marginTop: 20 }}
          />
          <OldTextField
            infoTooltip={phoneTooltip}
            name="user_phone2"
            label={t('Phone number (landline)')}
            style={{ marginTop: 20 }}
          />
          <OldTextField
            name="user_pgp_key"
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
              variant="outlined"
              color="primary"
              onClick={handleClose}
              style={{ marginRight: 10 }}
              disabled={submitting}
            >
              {t('Cancel')}
            </Button>
            <Button
              variant="contained"
              color="primary"
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

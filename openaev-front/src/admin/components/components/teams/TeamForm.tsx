import { Button } from '@mui/material';
import { type FunctionComponent, useContext } from 'react';
import { Form } from 'react-final-form';
import { makeStyles } from 'tss-react/mui';
import { z } from 'zod';

import { type TeamInputForm } from '../../../../actions/teams/Team';
import CheckboxField from '../../../../components/CheckboxField';
import OldTextField from '../../../../components/fields/OldTextField';
import { useFormatter } from '../../../../components/i18n';
import OrganizationField from '../../../../components/OrganizationField';
import TagField from '../../../../components/TagField';
import { schemaValidator } from '../../../../utils/Zod';
import { TeamContext, type TeamContextType } from '../../common/Context';

const useStyles = makeStyles()(theme => ({
  container: {
    display: 'flex',
    gap: theme.spacing(2),
    placeContent: 'end',
  },
}));

interface TeamFormProps {
  initialValues: Partial<TeamInputForm>;
  handleClose: () => void;
  onSubmit: (data: TeamInputForm) => void;
  editing?: boolean;
}

const TeamForm: FunctionComponent<TeamFormProps> = ({
  editing,
  onSubmit,
  initialValues,
  handleClose,
}) => {
  const { classes } = useStyles();
  const { t } = useFormatter();
  const { onCreateTeam } = useContext<TeamContextType>(TeamContext);
  const teamFormSchemaValidation = z.object({ team_name: z.string().min(2, t('This field is mandatory')) });
  return (
    <Form
      keepDirtyOnReinitialize
      initialValues={initialValues}
      onSubmit={onSubmit}
      validate={schemaValidator(teamFormSchemaValidation)}
      mutators={{
        setValue: ([field, value], state, { changeValue }) => {
          changeValue(state, field, () => value);
        },
      }}
    >
      {({ handleSubmit, form, values, submitting, pristine }) => (
        <form id="teamForm" onSubmit={handleSubmit}>
          <OldTextField
            variant="standard"
            name="team_name"
            fullWidth
            label={t('Name')}
          />
          <OldTextField
            variant="standard"
            name="team_description"
            fullWidth
            label={t('Description')}
            style={{ marginTop: 20 }}
          />
          <OrganizationField
            name="team_organization"
            values={values}
            setFieldValue={form.mutators.setValue}
          />
          <TagField
            name="team_tags"
            label={t('Tags')}
            values={values}
            setFieldValue={form.mutators.setValue}
            style={{ marginTop: 20 }}
          />
          {/* check if we are not editing and we have a onCreateTeam function in context */}
          {!editing && onCreateTeam && (
            <CheckboxField
              name="team_contextual"
              label={t('Only in this context')}
              style={{ marginTop: 20 }}
            />
          )}
          <div className={classes.container} style={{ marginTop: 20 }}>
            <Button variant="contained" onClick={handleClose} disabled={submitting}>
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

export default TeamForm;

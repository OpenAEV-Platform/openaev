import { AddOutlined, DomainOutlined } from '@mui/icons-material';
import { Autocomplete as MuiAutocomplete, Box, Dialog, DialogContent, DialogTitle, IconButton, TextField } from '@mui/material';
import { type FunctionComponent, useState } from 'react';
import { Controller, useFormContext } from 'react-hook-form';
import { makeStyles } from 'tss-react/mui';

import type { OrganizationHelper } from '../../actions/helper';
import { addOrganization, fetchOrganizations } from '../../actions/Organization';
import OrganizationForm from '../../admin/components/teams/organizations/OrganizationForm';
import { useHelper } from '../../store';
import { type Organization, type OrganizationCreateInput } from '../../utils/api-types';
import { useAppDispatch } from '../../utils/hooks';
import useDataLoader from '../../utils/hooks/useDataLoader';
import { type Option } from '../../utils/Option';
import { useFormatter } from '../i18n';

const useStyles = makeStyles()(theme => ({
  icon: {
    paddingTop: theme.spacing(1),
    display: 'inline-block',
  },
  text: {
    display: 'inline-block',
    flexGrow: 1,
    marginLeft: theme.spacing(1),
  },
  autoCompleteIndicator: { display: 'none' },
}));

interface Props {
  name: string;
  label: string;
}

const OrganizationFieldController: FunctionComponent<Props> = ({ name, label }) => {
  const { t } = useFormatter();
  const { classes } = useStyles();
  const dispatch = useAppDispatch();
  const { control, setValue } = useFormContext();

  const { organizations }: { organizations: Organization[] } = useHelper((helper: OrganizationHelper) => ({ organizations: helper.getOrganizations() }));

  useDataLoader(() => {
    dispatch(fetchOrganizations());
  });

  const [open, setOpen] = useState(false);

  const handleSubmit = async (data: Omit<OrganizationCreateInput, 'organization_tags'> & { organization_tags: Option[] }) => {
    const inputValues = {
      ...data,
      organization_tags: (data.organization_tags ?? []).map(t => t.id),
    };

    dispatch(addOrganization(inputValues)).then((result: {
      result: string;
      entities: { organizations: Record<string, Organization> };
    }) => {
      if (result.result) {
        setValue(name, result.result);
        setOpen(false);
      }
    });
  };

  const options: Option[] = organizations.map(o => ({
    id: o.organization_id,
    label: o.organization_name,
  }));

  return (
    <>
      <Controller
        name={name}
        control={control}
        render={({ field, fieldState: { error } }) => (
          <MuiAutocomplete
            {...field}
            value={options.find(o => o.id === field.value) || null}
            fullWidth
            multiple={false}
            options={options}
            onChange={(_, value) => field.onChange(value?.id || '')}
            getOptionLabel={option => option.label}
            isOptionEqualToValue={(option, value) => option.id === value.id}
            renderOption={(props, option) => (
              <Box component="li" {...props} key={option.id}>
                <div className={classes.icon}>
                  <DomainOutlined />
                </div>
                <div className={classes.text}>{option.label}</div>
              </Box>
            )}
            renderInput={params => (
              <TextField
                {...params}
                label={label}
                variant="standard"
                error={!!error}
                helperText={error?.message}
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
                          onClick={() => setOpen(true)}
                        >
                          <AddOutlined />
                        </IconButton>
                        {params.InputProps.endAdornment}
                      </>
                    ),
                  },
                }}
              />
            )}
            classes={{ clearIndicator: classes.autoCompleteIndicator }}
          />
        )}
      />
      <Dialog open={open} onClose={() => setOpen(false)} slotProps={{ paper: { elevation: 1 } }}>
        <DialogTitle>{t('Create a new organization')}</DialogTitle>
        <DialogContent>
          <OrganizationForm
            onSubmit={handleSubmit}
            initialValues={{ organization_tags: [] }}
            handleClose={() => setOpen(false)}
          />
        </DialogContent>
      </Dialog>
    </>
  );
};

export default OrganizationFieldController;

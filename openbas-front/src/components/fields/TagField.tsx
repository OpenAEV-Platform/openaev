import { AddOutlined, LabelOutlined } from '@mui/icons-material';
import { Autocomplete as MuiAutocomplete, Box, Dialog, DialogContent, DialogTitle, IconButton, TextField } from '@mui/material';
import * as R from 'ramda';
import { type CSSProperties, type FunctionComponent, useState } from 'react';
import { type GlobalError } from 'react-hook-form';
import { makeStyles } from 'tss-react/mui';

import { type TagHelper, type UserHelper } from '../../actions/helper';
import { addTag } from '../../actions/Tag';
import TagForm from '../../admin/components/settings/tags/TagForm';
import { useHelper } from '../../store';
import { type Tag } from '../../utils/api-types';
import { useAppDispatch } from '../../utils/hooks';
import { Can } from '../../utils/permissions/PermissionsProvider';
import { ACTIONS, SUBJECTS } from '../../utils/permissions/types';
import { useFormatter } from '../i18n';

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
  fieldValue: string[];
  fieldOnChange: (values: string[]) => void;
  error?: GlobalError;
  style?: CSSProperties;
  disabled?: boolean;
  required?: boolean;
}

const TagField: FunctionComponent<Props> = ({
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
  const { tags }: { tags: [Tag] } = useHelper((helper: TagHelper & UserHelper) => ({ tags: helper.getTags() }));
  const dispatch = useAppDispatch();

  // Handle tag creation
  const [tagCreation, setTagCreation] = useState(false);
  const handleOpenTagCreation = () => setTagCreation(true);
  const handleCloseTagCreation = () => setTagCreation(false);

  // Form
  const tagsOptions = tags.map(
    n => ({
      id: n.tag_id,
      label: n.tag_name,
      color: n.tag_color,
    }),
  );
  const values = () => {
    return tagsOptions.filter(tag => fieldValue.includes(tag.id));
  };

  const onSubmit = (data: Tag) => {
    dispatch(addTag(data))
      .then((result: {
        entities: { tags: Record<string, Tag> };
        result: string;
      }) => {
        if (result.result) {
          const newTag = result.entities.tags[result.result];
          const newTags = R.append(
            newTag.tag_id,
            fieldValue,
          );
          fieldOnChange(newTags);
          handleCloseTagCreation();
        }
        return result;
      });
  };

  return (
    <div style={{
      position: 'relative',
      ...style,
    }}
    >
      <MuiAutocomplete
        value={values()}
        size="small"
        multiple
        selectOnFocus
        autoHighlight
        clearOnBlur={false}
        clearOnEscape={false}
        disabled={disabled}
        options={tagsOptions}
        onChange={(_, value) => {
          fieldOnChange(value.map(v => v.id));
        }}
        renderOption={(props, option) => (
          <Box component="li" {...props} key={option.id}>
            <div className={classes.icon} style={{ color: option.color }}>
              <LabelOutlined />
            </div>
            <div className={classes.text}>{option.label}</div>
          </Box>
        )}
        isOptionEqualToValue={(option, value) => option.id === value.id}
        renderInput={params => (
          <TextField
            {...params}
            label={label}
            variant="standard"
            fullWidth
            error={!!error}
            required={required}
            slotProps={{
              input: {
                ...params.InputProps,
                endAdornment: (
                  <>
                    <Can I={ACTIONS.MANAGE} a={SUBJECTS.PLATFORM_SETTINGS}>
                      <IconButton
                        style={{
                          position: 'absolute',
                          right: '35px',
                        }}
                        disabled={disabled}
                        onClick={() => handleOpenTagCreation()}
                      >
                        <AddOutlined />
                      </IconButton>
                    </Can>
                    {params.InputProps.endAdornment}
                  </>
                ),
              },
            }}
          />
        )}
        classes={{ clearIndicator: classes.autoCompleteIndicator }}
      />
      <Can I={ACTIONS.MANAGE} a={SUBJECTS.PLATFORM_SETTINGS}>
        <Dialog
          open={tagCreation}
          onClose={handleCloseTagCreation}
          PaperProps={{ elevation: 1 }}
        >
          <DialogTitle>{t('Create a new tag')}</DialogTitle>
          <DialogContent>
            <TagForm
              onSubmit={onSubmit}
              handleClose={handleCloseTagCreation}
            />
          </DialogContent>
        </Dialog>
      </Can>
    </div>
  );
};

export default TagField;

import { AddOutlined, LabelOutlined } from '@mui/icons-material';
import { Autocomplete as MuiAutocomplete, Box, Dialog, DialogContent, DialogTitle, IconButton, TextField } from '@mui/material';
import { type CSSProperties, type FunctionComponent, useState } from 'react';
import { type FieldErrors } from 'react-hook-form';
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
  name: string;
  label: string;
  fieldValue: string;
  fieldOnChange: (values: string) => void;
  errors: FieldErrors;
  style: CSSProperties;
  disabled: boolean;
  forbiddenOptions?: string[];
}

const TagFieldSingle: FunctionComponent<Props> = ({
  name,
  label,
  fieldValue,
  fieldOnChange,
  errors,
  style,
  disabled,
  forbiddenOptions = [],
}) => {
  // Standard hooks
  const { t } = useFormatter();
  const { classes } = useStyles();

  // Fetching data
  const { tags }: { tags: [Tag] } = useHelper((helper: TagHelper & UserHelper) => ({ tags: helper.getTags().filter(tag => !forbiddenOptions.includes(tag.tag_name)) }));
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

  const value = () => {
    return tagsOptions.filter(tag => fieldValue.includes(tag.label))[0];
  };

  const onSubmit = (data: Tag) => {
    dispatch(addTag(data))
      .then((result: {
        entities: { tags: Record<string, Tag> };
        result: string;
      }) => {
        if (result.result) {
          const newTag = result.entities.tags[result.result];
          fieldOnChange(newTag.tag_name);
          handleCloseTagCreation();
        }
        return result;
      });
  };

  return (
    <div style={{ position: 'relative' }}>
      <MuiAutocomplete
        value={value()}
        size="small"
        disabled={disabled}
        selectOnFocus
        autoHighlight
        clearOnBlur={false}
        clearOnEscape={false}
        options={tagsOptions}
        onChange={(_, value) => {
          fieldOnChange(value?.label ?? '');
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
            style={style}
            error={!!errors[name]}
          />
        )}
        classes={{ clearIndicator: classes.autoCompleteIndicator }}
      />
      <IconButton
        onClick={handleOpenTagCreation}
        edge="end"
        style={{
          position: 'absolute',
          top: 30,
          right: 35,
        }}
        disabled={disabled}
      >
        <AddOutlined />
      </IconButton>
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

export default TagFieldSingle;

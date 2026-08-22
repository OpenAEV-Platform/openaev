import { AddOutlined, LabelOutlined } from '@mui/icons-material';
import { Box, Dialog, DialogContent, DialogTitle, IconButton, TextField } from '@mui/material';
import {
  Combobox,
  ComboboxContent,
  ComboboxControls,
  ComboboxField,
  ComboboxInput,
  ComboboxLabel,
  ComboboxTrigger,
} from '@filigran/design-system';
import { type CSSProperties, type FunctionComponent, useState } from 'react';
import { type FieldErrors } from 'react-hook-form';
import { makeStyles } from 'tss-react/mui';

import { type UserHelper } from '../../actions/helper';
import { addTag } from '../../actions/tags/tag-action';
import { type TagHelper } from '../../actions/tags/tag-helper';
import TagForm from '../../admin/components/settings/tags/TagForm';
import { useHelper } from '../../store';
import { type Tag, type TagCreateInput } from '../../utils/api-types';
import { useAppDispatch } from '../../utils/hooks';
import { Can } from '../../utils/permissions/permissionsContext';
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

  const onSubmit = (data: TagCreateInput) => {
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
      <Combobox<{ id: string; label: string; color: string | undefined }>
        options={tagsOptions}
        value={value() ?? null}
        onValueChange={(next) => {
          fieldOnChange((next as { label: string } | null)?.label ?? '');
        }}
        getOptionLabel={option => option.label}
        isOptionEqualToValue={(option, v) => option.id === v.id}
        disabled={disabled}
        error={!!errors[name]}
        // The MUI field hid its clear control via a `classes` override.
        clearable={false}
        renderOption={option => (
          <>
            {/* The tint comes from the tag's own data and stays on the glyph, never behind text. */}
            <div className={classes.icon} style={{ color: option.color }}>
              <LabelOutlined />
            </div>
            <div className={classes.text}>{option.label}</div>
          </>
        )}
      >
        <ComboboxLabel>{label}</ComboboxLabel>
        <ComboboxField>
          <ComboboxInput />
          <ComboboxControls>
            <ComboboxTrigger />
          </ComboboxControls>
        </ComboboxField>
        <ComboboxContent />
      </Combobox>
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
      <Can I={ACTIONS.MANAGE} a={SUBJECTS.TENANT_SETTINGS}>
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

import {
  Combobox,
  ComboboxChips,
  ComboboxClear,
  ComboboxContent,
  ComboboxControls,
  ComboboxField,
  ComboboxInput,
  ComboboxLabel,
  ComboboxTrigger,
  IconButton,
} from '@filigran/design-system';
import { AddOutlined, LabelOutlined } from '@mui/icons-material';
import {
  Dialog,
  DialogContent,
  DialogTitle,
} from '@mui/material';
import * as R from 'ramda';
import { type CSSProperties, type FunctionComponent, useState } from 'react';
import { type GlobalError } from 'react-hook-form';
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

type TagOption = {
  id: string;
  label: string;
  color?: string;
};

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

  const onSubmit = (data: TagCreateInput) => {
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
      <Combobox<TagOption>
        multiple
        value={values()}
        options={tagsOptions}
        disabled={disabled}
        selectOnFocus
        keepInputOnBlur
        onValueChange={(value) => {
          fieldOnChange((value as TagOption[]).map(v => v.id));
        }}
        getOptionLabel={option => option.label}
        isOptionEqualToValue={(option, value) => option.id === value.id}
        error={!!error}
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
        <ComboboxLabel>
          {label}
          {required ? ' *' : ''}
        </ComboboxLabel>
        <ComboboxField
          adornment={(
            // `<Can>` renders nothing without the ability; the slot is `empty:hidden`,
            // so it then costs neither width nor the shell's gap.
            <Can I={ACTIONS.MANAGE} a={SUBJECTS.TENANT_SETTINGS}>
              <IconButton
                size="sm"
                priority="tertiary"
                disabled={disabled}
                onClick={() => handleOpenTagCreation()}
                aria-label={t('Create a new tag')}
                icon={<AddOutlined fontSize="small" />}
              />
            </Can>
          )}
        >
          <ComboboxChips />
          <ComboboxInput />
          <ComboboxControls>
            <ComboboxClear />
            <ComboboxTrigger />
          </ComboboxControls>
        </ComboboxField>
        <ComboboxContent />
      </Combobox>
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

export default TagField;

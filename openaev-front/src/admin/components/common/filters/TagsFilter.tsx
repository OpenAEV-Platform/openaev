import {
  Combobox,
  ComboboxContent,
  ComboboxControls,
  ComboboxField,
  ComboboxInput,
  ComboboxTrigger,
} from '@filigran/design-system';
import { LabelOutlined } from '@mui/icons-material';
import { Box, Chip } from '@mui/material';
import { type FunctionComponent, useState } from 'react';

import { type TagHelper } from '../../../../actions/tags/tag-helper';
import { useFormatter } from '../../../../components/i18n';
import { useHelper } from '../../../../store';
import { type Tag } from '../../../../utils/api-types';
import { type Option } from '../../../../utils/Option';

interface Props {
  currentTags: Option[];
  onAddTag: (value: Option) => void;
  onRemoveTag?: (value: Option['id']) => void;
  onClearTag?: () => void;
  fullWidth?: boolean;
}

const TagsFilter: FunctionComponent<Props> = ({
  currentTags,
  onAddTag,
  onRemoveTag,
  onClearTag,
  fullWidth = false,
}) => {
  // The library Combobox is always controlled ("there is no uncontrolled mode"),
  // so the field's own transient selection now lives here — it is what the
  // uncontrolled MUI Autocomplete used to keep internally.
  const [selected, setSelected] = useState<Option | null>(null);
  // The component holds the input's text itself: clearing `value` alone does not
  // clear the text the library wrote there when the option was picked.
  const [inputValue, setInputValue] = useState('');
  const { t } = useFormatter();
  const { tags } = useHelper((helper: TagHelper) => ({ tags: helper.getTags() }));

  const tagTransform = (tag: Tag): Option => ({
    id: tag.tag_id,
    label: tag.tag_name,
    color: tag.tag_color,
  });
  const tagsOptions = tags
    .map(tagTransform)
    .filter((option: Option) => !currentTags.some(currentTag => currentTag.id === option.id));

  return (
    <>
      <div style={{
        width: fullWidth ? '100%' : 250,
        flexShrink: 0,
      }}
      >
        <Combobox<Option>
          labelPosition="none"
          openOnFocus
          options={tagsOptions}
          value={selected}
          inputValue={inputValue}
          onInputChange={(next, meta) => {
            if (meta.cause === 'type') {
              setInputValue(next);
            }
          }}
          onValueChange={(next) => {
            // The library types this callback for both modes; this field is
            // single, so the value is one option or nothing.
            const value = next as Option | null;
            // MUI reported a `clear` reason here; in single mode a cleared field
            // is exactly a null value, so the two paths stay distinguishable.
            if (value !== null) {
              onAddTag(value);
            } else if (fullWidth) {
              onClearTag?.();
            }
            // The chosen tag leaves the field for the chip row below, so the
            // field holds nothing and shows its placeholder again.
            setSelected(null);
            setInputValue('');
          }}
          getOptionLabel={option => option.label}
          // The library Combobox hands this an empty string before anything is
          // picked — a runtime shape its own type does not describe, which is why
          // the guard is a truthiness test and not a comparison to ''.
          isOptionEqualToValue={(option, value) => !value || option.id === value.id}
          renderOption={option => (
            <>
              {/* The tint comes from the tag's own data and stays on the glyph. */}
              <div style={{
                paddingTop: 4,
                display: 'inline-block',
                color: option.color,
              }}
              >
                <LabelOutlined />
              </div>
              <div style={{
                display: 'inline-block',
                flexGrow: 1,
                marginLeft: 10,
              }}
              >
                {option.label}
              </div>
            </>
          )}
        >
          <ComboboxField>
            <ComboboxInput
              placeholder={t('Tags')}
              aria-label={t('Tags')}
            />
            <ComboboxControls>
              <ComboboxTrigger />
            </ComboboxControls>
          </ComboboxField>
          <ComboboxContent />
        </Combobox>
      </div>
      {!fullWidth && (
        <Box
          component="div"
          sx={{
            display: 'flex',
            flexWrap: 'wrap',
            alignItems: 'center',
            gap: 1,
            minWidth: 0,
          }}
        >
          {currentTags.map(currentTag => (
            <Chip
              key={currentTag.id}
              label={currentTag.label}
              onDelete={() => onRemoveTag?.(currentTag.id)}
            />
          ))}
        </Box>
      )}
    </>
  );
};

export default TagsFilter;

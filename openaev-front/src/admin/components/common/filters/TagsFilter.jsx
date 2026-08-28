import {
  Combobox,
  ComboboxContent,
  ComboboxControls,
  ComboboxField,
  ComboboxInput,
  ComboboxTrigger,
} from '@filigran/design-system';
import { LabelOutlined } from '@mui/icons-material';
import { Chip } from '@mui/material';
import * as R from 'ramda';
import { useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { useFormatter } from '../../../../components/i18n';
import { useHelper } from '../../../../store';

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
  filters: {
    // The chips sit in the same flex row as the search box and this field, which
    // centres its items. A top margin pushed them 3px below that centre line,
    // and `float` does nothing to a flex item — both were left over from the
    // pre-flex layout.
    display: 'flex',
    alignItems: 'center',
    gap: 8,
  },
}));

const TagsFilter = (props) => {
  const { classes } = useStyles();
  // The library Combobox is always controlled ("there is no uncontrolled mode"),
  // so the field's own transient selection now lives here — it is what the
  // uncontrolled MUI Autocomplete used to keep internally.
  const [selected, setSelected] = useState(null);
  // The component holds the input's text itself: clearing `value` alone does not
  // clear the text the library wrote there when the option was picked.
  const [inputValue, setInputValue] = useState('');
  const { t } = useFormatter();
  const { tags } = useHelper(helper => ({ tags: helper.getTags() }));
  const { onAddTag, onClearTag, onRemoveTag, currentTags, fullWidth } = props;
  const tagTransform = n => ({
    id: n.tag_id,
    label: n.tag_name,
    color: n.tag_color,
  });
  const tagsOptions = tags.map(tagTransform);
  return (
    <>
      <div style={{
        width: fullWidth ? '100%' : 250,
        float: 'left',
      }}
      >
        <Combobox
          openOnFocus
          options={tagsOptions}
          value={selected}
          inputValue={inputValue}
          onInputChange={(next, meta) => {
            if (meta.cause === 'type') {
              setInputValue(next);
            }
          }}
          onValueChange={(value) => {
            // MUI reported a `clear` reason here; in single mode a cleared field
            // is exactly a null value, so the two paths stay distinguishable.
            if (value !== null) {
              onAddTag(value);
            } else if (fullWidth) {
              onClearTag();
            }
            // The chosen tag leaves the field for the chip row below, so the
            // field holds nothing and shows its placeholder again. It used to
            // keep the tag as its own value AND the library's own text, so both
            // are cleared.
            setSelected(null);
            setInputValue('');
          }}
          getOptionLabel={option => option.label}
          isOptionEqualToValue={(option, value) => value === undefined || value === '' || option.id === value.id}
          renderOption={option => (
            <>
              {/* The tint comes from the tag's own data and stays on the glyph. */}
              <div className={classes.icon} style={{ color: option.color }}>
                <LabelOutlined />
              </div>
              <div className={classes.text}>{option.label}</div>
            </>
          )}
        >
          <ComboboxField>
            <ComboboxInput placeholder={t('Tags')} />
            <ComboboxControls>
              <ComboboxTrigger />
            </ComboboxControls>
          </ComboboxField>
          <ComboboxContent />
        </Combobox>
      </div>
      {!fullWidth && (
        <div className={classes.filters}>
          {R.map(
            currentTag => (
              <Chip
                key={currentTag.id}
                label={currentTag.label}
                onDelete={() => onRemoveTag(currentTag.id)}
              />
            ),
            currentTags,
          )}
        </div>
      )}
    </>
  );
};

export default TagsFilter;

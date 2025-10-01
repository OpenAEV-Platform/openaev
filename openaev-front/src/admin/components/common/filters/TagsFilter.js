import { LabelOutlined } from '@mui/icons-material';
import { Autocomplete, Box, Chip, TextField } from '@mui/material';
import * as R from 'ramda';
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
    float: 'left',
    margin: '5px 0 0 0',
  },
  filter: { marginRight: 10 },
}));

const TagsFilter = (props) => {
  const { classes } = useStyles();
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
      <Autocomplete
        sx={{
          width: fullWidth ? '100%' : 250,
          float: 'left',
          marginRight: '10px',
        }}
        selectOnFocus={true}
        openOnFocus={true}
        autoSelect={false}
        autoHighlight={true}
        size="small"
        options={tagsOptions}
        onChange={(event, value, reason) => {
          // When removing, a null change is fired
          // We handle directly the remove through the chip deletion.
          if (value !== null) onAddTag(value);
          if (reason === 'clear' && fullWidth) onClearTag();
        }}
        isOptionEqualToValue={(option, value) => value === undefined || value === '' || option.id === value.id}
        renderOption={(p, option) => (
          <Box component="li" {...p} key={option.id}>
            <div className={classes.icon} style={{ color: option.color }}>
              <LabelOutlined />
            </div>
            <div className={classes.text}>{option.label}</div>
          </Box>
        )}
        renderInput={params => (
          <TextField
            label={t('Tags')}
            size="small"
            fullWidth={true}
            variant="outlined"
            {...params}
          />
        )}
      />
      {!fullWidth && (
        <div className={classes.filters}>
          {R.map(
            currentTag => (
              <Chip
                key={currentTag.id}
                classes={{ root: classes.filter }}
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

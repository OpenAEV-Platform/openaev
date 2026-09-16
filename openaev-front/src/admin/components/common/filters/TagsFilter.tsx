import { LabelOutlined } from '@mui/icons-material';
import { Autocomplete, Box, Chip, TextField } from '@mui/material';
import { type FunctionComponent } from 'react';

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
      <Autocomplete<Option>
        sx={{
          width: fullWidth ? '100%' : 250,
          flexShrink: 0,
        }}
        selectOnFocus
        openOnFocus
        autoSelect={false}
        autoHighlight
        size="small"
        options={tagsOptions}
        onChange={(_event, value, reason) => {
          // When removing, a null change is fired
          // We handle directly the remove through the chip deletion.
          if (value !== null) onAddTag(value);
          if (reason === 'clear' && fullWidth) onClearTag?.();
        }}
        isOptionEqualToValue={(option, value) => option.id === value?.id}
        renderOption={(props, option) => (
          <Box component="li" {...props} key={option.id}>
            <Box
              sx={{
                paddingTop: '4px',
                display: 'inline-block',
                color: option.color,
              }}
            >
              <LabelOutlined />
            </Box>
            <Box
              sx={{
                display: 'inline-block',
                flexGrow: 1,
                marginLeft: '10px',
              }}
            >
              {option.label}
            </Box>
          </Box>
        )}
        renderInput={params => (
          <TextField
            {...params}
            label={t('Tags')}
            size="small"
            fullWidth
            variant="outlined"
          />
        )}
      />
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

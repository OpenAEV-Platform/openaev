import { Chip, Tooltip } from '@mui/material';
import * as PropTypes from 'prop-types';
import { useMemo } from 'react';
import { makeStyles } from 'tss-react/mui';

import { useHelper } from '../store';
import {
  getLabelOfRemainingItems,
  getRemainingItemsCount,
  getVisibleItems,
  truncate,
} from '../utils/String';
import Tag from './common/tag/Tag';

const useStyles = makeStyles()(() => ({
  inline: {
    display: 'inline',
    alignItems: 'center',
    flexWrap: 'nowrap',
    overflow: 'hidden',
  },
  tag: {
    height: 25,
    fontSize: 12,
    margin: '0 7px 7px 0',
    borderRadius: 4,
  },
  tagInList: {
    float: 'left',
    height: 20,
    margin: '0 7px 0 0',
  },
}));

const ItemTags = (props) => {
  const { tags, variant, limit = 2 } = props;
  const { classes } = useStyles();

  const { allTags } = useHelper(helper => ({ allTags: helper.getTags() }));

  const resolvedTags = useMemo(
    () => allTags.filter(tag => (tags ?? []).includes(tag.tag_id)),
    [allTags, tags],
  );

  const orderedTags = useMemo(
    () =>
      [...resolvedTags].sort((a, b) =>
        a.tag_name.localeCompare(b.tag_name),
      ),
    [resolvedTags],
  );

  const visibleTags = getVisibleItems(orderedTags, limit);
  const tooltipLabel = getLabelOfRemainingItems(
    orderedTags,
    limit,
    'tag_name',
  );
  const remainingTagsCount = getRemainingItemsCount(
    orderedTags,
    visibleTags,
  );

  const truncateLimit = variant === 'reduced-view' ? 6 : 15;

  return (
    <div className={classes.inline}>
      {visibleTags.length > 0 ? (
        visibleTags.map(tag => (
          <span
            key={tag.tag_id}
            style={{
              marginRight: 7,
              marginBottom: variant === 'list' || variant === 'reduced-view' ? 0 : 7,
              display: 'inline-block',
            }}
          >
            <Tag
              label={truncate(tag.tag_name, truncateLimit)}
              color={tag.tag_color}
            />
          </span>
        ))
      ) : (
        <span>-</span>
      )}

      {remainingTagsCount > 0 && (
        <Tooltip title={tooltipLabel}>
          <Chip
            variant="outlined"
            classes={{ root: variant === 'list' || variant === 'reduced-view' ? `${classes.tag} ${classes.tagInList}` : classes.tag }}
            label={`+${remainingTagsCount}`}
          />
        </Tooltip>
      )}
    </div>
  );
};

ItemTags.propTypes = {
  variant: PropTypes.string,
  onClick: PropTypes.func,
  tags: PropTypes.array,
  limit: PropTypes.number,
};

export default ItemTags;

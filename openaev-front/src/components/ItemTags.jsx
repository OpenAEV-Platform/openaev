import { Chip, Tooltip } from '@mui/material';
import * as PropTypes from 'prop-types';
import { useMemo } from 'react';
import { makeStyles } from 'tss-react/mui';

import { useHelper } from '../store';
import { hexToRGB } from '../utils/Colors';
import {
  getLabelOfRemainingItems,
  getRemainingItemsCount,
  getVisibleItems,
  truncate,
} from '../utils/String';

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

  let style = classes.tag;
  let truncateLimit = 15;

  if (variant === 'list') {
    style = `${classes.tag} ${classes.tagInList}`;
  }

  if (variant === 'reduced-view') {
    style = `${classes.tag} ${classes.tagInList}`;
    truncateLimit = 6;
  }

  const { allTags } = useHelper(helper => ({ allTags: helper.getTags() }));

  const resolvedTags = useMemo(
    () => allTags.filter(tag => (tags ?? []).includes(tag.tag_id)),
    [allTags, tags],
  );

  // 🔥 Remplacement de Ramda.sortWith / ascend / prop
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

  return (
    <div className={classes.inline}>
      {visibleTags.length > 0 ? (
        visibleTags.map(tag => (
          <span key={tag.tag_id}>
            <Tooltip title={tag.tag_name}>
              <Chip
                variant="outlined"
                classes={{ root: style }}
                label={truncate(tag.tag_name, truncateLimit)}
                style={{
                  color: tag.tag_color,
                  borderColor: tag.tag_color,
                  backgroundColor: hexToRGB(tag.tag_color),
                }}
              />
            </Tooltip>
          </span>
        ))
      ) : (
        <span>-</span>
      )}

      {remainingTagsCount > 0 && (
        <Tooltip title={tooltipLabel}>
          <Chip
            variant="outlined"
            classes={{ root: style }}
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

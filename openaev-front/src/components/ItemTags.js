import { Chip, Tooltip } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import * as PropTypes from 'prop-types';
import * as R from 'ramda';
import { useMemo } from 'react';
import { makeStyles } from 'tss-react/mui';

import { useHelper } from '../store';
import { hexToRGB } from '../utils/Colors';
import { getLabelOfRemainingItems, getRemainingItemsCount, getVisibleItems, truncate } from '../utils/String';
import { useFormatter } from './i18n';

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
  const { t } = useFormatter();
  const theme = useTheme();
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
  const { allTags } = useHelper((helper) => {
    const allTags = helper.getTags();
    return { allTags };
  });
  const resolvedTags = useMemo(() => allTags.filter(tag => (tags ?? []).includes(tag.tag_id)), [allTags, tags]);
  const orderedTags = R.sortWith([R.ascend(R.prop('tag_name'))], resolvedTags);

  const visibleTags = getVisibleItems(orderedTags, limit);
  const tooltipLabel = getLabelOfRemainingItems(orderedTags, limit, 'tag_name');
  const remainingTagsCount = getRemainingItemsCount(orderedTags, visibleTags);

  return (
    <div className={classes.inline}>
      {visibleTags.length > 0
        ? (visibleTags.map(
            (tag, index) => (
              <span key={index}>
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
            ),
          )) : (
            <Chip
              classes={{ root: style }}
              variant="outlined"
              label={t('No tag')}
              style={{
                color: theme.palette.mode === 'dark' ? '#ffffff' : '#000000',
                borderColor: theme.palette.mode === 'dark'
                  ? '#ffffff'
                  : '#000000',
                backgroundColor: hexToRGB(
                  theme.palette.mode === 'dark' ? '#ffffff' : 'transparent',
                ),
              }}
            />
          )}
      {remainingTagsCount && remainingTagsCount > 0 && (
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

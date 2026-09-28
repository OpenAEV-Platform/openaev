import { Chip, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { isImmutable } from 'immutable';
import * as PropTypes from 'prop-types';
import { useCallback, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { makeStyles } from 'tss-react/mui';

import { useHelper } from '../store';
import { truncate } from '../utils/String';

const GAP = 6;

const useStyles = makeStyles()(() => ({
  inline: {
    'display': 'flex',
    'alignItems': 'center',
    // Tags read as one row, never as a stack: a cell one line tall would clip
    // the second line anyway. What does not fit is counted in the +N chip.
    'flexWrap': 'nowrap',
    'overflow': 'hidden',
    'gap': GAP,
    '& [data-tag-chip], & [data-tag-chip] > span': { minWidth: 0 },
    '& [data-tag-overflow]': { flexShrink: 0 },
    '&[data-measuring] [data-tag-chip]': { flexShrink: 0 },
  },
}));

const ItemTags = (props) => {
  const { tags, variant, limit = 2 } = props;
  const { classes } = useStyles();

  let truncateLimit = 15;

  if (variant === 'reduced-view') {
    truncateLimit = 6;
  }

  // Resolve only this row's tags instead of subscribing to (and converting) the entire tags
  // collection per rendered row: in a 50-row list that used to mean 50 full toJS conversions of
  // the tags store on every dispatch
  const { resolvedTags } = useHelper(helper => ({
    resolvedTags: (tags ?? [])
      .map(tagId => helper.getTag(tagId))
      .filter(tag => !!tag)
      .map(tag => (isImmutable(tag) ? tag.toJS() : tag)),
  }));

  const orderedTags = useMemo(
    () =>
      [...resolvedTags].sort((a, b) =>
        a.tag_name.localeCompare(b.tag_name),
      ),
    [resolvedTags],
  );

  const capped = useMemo(() => orderedTags.slice(0, limit), [orderedTags, limit]);
  const allNames = useMemo(() => orderedTags.map(tag => tag.tag_name).join(', '), [orderedTags]);
  const key = useMemo(() => capped.map(tag => tag.tag_id).join('|'), [capped]);

  const container = useRef(null);
  const widths = useRef({
    key: null,
    chips: [],
    plus: 0,
  });
  // Everything is rendered for one pass so each chip can be measured; the count
  // that fits is then what stays.
  const [visibleCount, setVisibleCount] = useState(capped.length);

  const fit = useCallback(() => {
    const node = container.current;
    const { chips, plus } = widths.current;
    if (!node || chips.length === 0) return;
    const available = node.clientWidth;
    let used = 0;
    let count = 0;
    for (let i = 0; i < chips.length; i += 1) {
      const next = used + (i > 0 ? GAP : 0) + chips[i];
      const hidden = chips.length - (i + 1) + (orderedTags.length - chips.length);
      const reserved = hidden > 0 ? GAP + plus : 0;
      if (next + reserved > available) break;
      used = next;
      count = i + 1;
    }
    // One chip always shows, however narrow the column: an empty cell with a
    // bare "+2" says less than a truncated name does.
    setVisibleCount(Math.max(count, 1));
  }, [orderedTags.length]);

  useLayoutEffect(() => {
    const node = container.current;
    if (!node) return undefined;
    if (widths.current.key !== key) {
      // Measured while every chip is on screen, then kept: a chip's width only
      // changes when its text does, which changes `key`.
      const measured = [...node.querySelectorAll('[data-tag-chip]')].map(el => el.getBoundingClientRect().width);
      if (measured.length === capped.length) {
        const plusNode = node.querySelector('[data-tag-overflow]');
        widths.current = {
          key,
          chips: measured,
          plus: plusNode ? plusNode.getBoundingClientRect().width : 34,
        };
      }
    }
    fit();
    const observer = new ResizeObserver(fit);
    observer.observe(node);
    return () => observer.disconnect();
  }, [key, capped.length, fit]);

  // Before the first measurement every chip is rendered, so each one can be read.
  const measuring = widths.current.key !== key;
  const shown = measuring ? capped : capped.slice(0, visibleCount);
  const hiddenCount = orderedTags.length - shown.length;

  return (
    <div className={classes.inline} ref={container} data-measuring={measuring ? '' : undefined}>
      {shown.length > 0 ? (
        shown.map(tag => (
          <Tooltip key={tag.tag_id}>
            <TooltipTrigger asChild>
              <Chip data-tag-chip label={truncate(tag.tag_name, truncateLimit)} color={tag.tag_color} />
            </TooltipTrigger>
            {tag.tag_name && <TooltipContent>{tag.tag_name}</TooltipContent>}
          </Tooltip>
        ))
      ) : (
        <span>-</span>
      )}

      {(hiddenCount > 0 || measuring) && orderedTags.length > 0 && (
        <Tooltip>
          <TooltipTrigger asChild>
            <Chip
              data-tag-overflow
              label={`+${Math.max(hiddenCount, 1)}`}
              style={measuring && hiddenCount <= 0 ? {
                position: 'absolute',
                visibility: 'hidden',
              } : undefined}
            />
          </TooltipTrigger>
          {/* The whole list, not just what is hidden: the point of the chip is
              to answer "which tags does this row carry". */}
          <TooltipContent>{allNames}</TooltipContent>
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

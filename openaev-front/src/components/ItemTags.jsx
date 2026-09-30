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
    // One row, never a stack: what does not fit is counted in the +N chip.
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

  // Only this row's tags: subscribing to the whole store meant one toJS per row, per dispatch.
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
  const key = useMemo(() => capped.map(tag => tag.tag_id).join('|'), [capped]);

  const container = useRef(null);
  const widths = useRef({
    key: null,
    chips: [],
    plus: 0,
  });
  // One pass with every chip rendered, so each can be measured.
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
    // One chip always shows: a bare "+2" says less than a truncated name.
    setVisibleCount(Math.max(count, 1));
  }, [orderedTags.length]);

  useLayoutEffect(() => {
    const node = container.current;
    if (!node) return undefined;
    if (widths.current.key !== key) {
      // A chip's width only changes when its text does, which changes `key`.
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

  const measuring = widths.current.key !== key;
  const shown = measuring ? capped : capped.slice(0, visibleCount);
  const hiddenCount = orderedTags.length - shown.length;
  // Only what the "+N" stands for: the tags already shown are not repeated.
  const hiddenNames = orderedTags.slice(shown.length).map(tag => tag.tag_name).join(', ');

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
          {hiddenNames && <TooltipContent>{hiddenNames}</TooltipContent>}
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

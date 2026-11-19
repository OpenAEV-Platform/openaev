import { Chip, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';

import useChipOverflow from '../../integrations/catalog_connectors/useChipOverflow';

interface Props { tags: string[] }

/** The tags on one line: the ones that do not fit collapse into a "+N" chip, whose tooltip lists them. */
const NewsFeedTags = ({ tags }: Props) => {
  const { containerRef, chipRefs, overflowRef, visibleCount } = useChipOverflow(tags);
  const hiddenCount = tags.length - visibleCount;

  return (
    <div
      ref={containerRef}
      style={{
        display: 'flex',
        flexWrap: 'nowrap',
        gap: 8,
        overflow: 'hidden',
        position: 'relative',
        minWidth: 0,
      }}
    >
      {tags.map((tag, index) => {
        const isVisible = index < visibleCount;
        return (
          <div
            key={tag}
            ref={(element) => {
              chipRefs.current[index] = element;
            }}
            // Measured even when hidden, so the number of chips that fit can be computed.
            style={{
              flexShrink: 0,
              visibility: isVisible ? 'visible' : 'hidden',
              position: isVisible ? 'relative' : 'absolute',
            }}
          >
            <Chip severity="info" label={tag} />
          </div>
        );
      })}
      {/* Always rendered so that its width is reserved; only shown when chips are hidden. */}
      <div
        ref={overflowRef}
        style={{
          flexShrink: 0,
          visibility: hiddenCount > 0 ? 'visible' : 'hidden',
          position: hiddenCount > 0 ? 'relative' : 'absolute',
        }}
      >
        <Tooltip>
          <TooltipTrigger asChild>
            <Chip severity="info" label={`+${Math.max(hiddenCount, 1)}`} />
          </TooltipTrigger>
          {hiddenCount > 0 && <TooltipContent>{tags.slice(visibleCount).join(', ')}</TooltipContent>}
        </Tooltip>
      </div>
    </div>
  );
};

export default NewsFeedTags;

import { Chip, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { RowingOutlined } from '@mui/icons-material';
import { type MouseEvent } from 'react';
import { makeStyles } from 'tss-react/mui';

const useStyles = makeStyles()(() => ({
  inline: {
    'display': 'flex',
    'alignItems': 'center',
    // One row, never a stack: a stack overflowed the 50 px row into the next one (#7696).
    'flexWrap': 'nowrap',
    'overflow': 'hidden',
    'gap': 6,
    'minWidth': 0,
    // The chip shrinks and its label ellipsises instead of running under the next column.
    '& [data-linked-chip], & [data-linked-chip] > span': { minWidth: 0 },
    '& [data-linked-overflow]': { flexShrink: 0 },
  },
}));

export interface LinkedChipItem {
  id: string;
  name?: string;
}

interface Props {
  // Every linked id of the document, resolved or not: the "+N" counts them all.
  ids: string[];
  resolve: (id: string) => LinkedChipItem | undefined;
  onOpen: (id: string) => void;
}

/**
 * The simulations or scenarios linked to a document, on one line: the first one as a chip,
 * the rest as a "+N" chip whose tooltip names them.
 */
const DocumentLinkedChips = ({ ids, resolve, onOpen }: Props) => {
  const { classes } = useStyles();
  const resolved = ids.map(resolve).filter((item): item is LinkedChipItem => !!item);
  if (resolved.length === 0) {
    return <span>-</span>;
  }
  const [first] = resolved;
  const hiddenCount = ids.length - 1;
  const hiddenNames = resolved.slice(1).map(item => item.name).filter(Boolean).join(', ');
  const open = (event: MouseEvent) => {
    event.stopPropagation();
    event.preventDefault();
    onOpen(first.id);
  };
  return (
    <div className={classes.inline}>
      <Tooltip>
        <TooltipTrigger asChild>
          <Chip
            data-linked-chip
            startIcon={<RowingOutlined style={{ fontSize: 12 }} />}
            label={first.name}
            onClick={open}
          />
        </TooltipTrigger>
        {first.name && <TooltipContent>{first.name}</TooltipContent>}
      </Tooltip>
      {hiddenCount > 0 && (
        <Tooltip>
          <TooltipTrigger asChild>
            <Chip data-linked-overflow label={`+${hiddenCount}`} />
          </TooltipTrigger>
          {hiddenNames && <TooltipContent>{hiddenNames}</TooltipContent>}
        </Tooltip>
      )}
    </div>
  );
};

export default DocumentLinkedChips;

import { Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';

// A tooltip whose text may be empty: the child then renders alone, without a bubble.
export default function CustomTooltip({ children, title }) {
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <span style={{ lineHeight: '20px' }}>{children}</span>
      </TooltipTrigger>
      {title && <TooltipContent>{title}</TooltipContent>}
    </Tooltip>
  );
}

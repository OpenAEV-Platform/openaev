import { type CSSProperties, forwardRef } from 'react';

import { buildTenantApiPath } from '../../../../utils/url-helper';

interface Props {
  type: string;
  style: CSSProperties;
  onClick?: () => void;
}

// forwardRef + prop spreading is required so that a parent MUI <Tooltip> (e.g. via InjectIcon)
// can attach its ref (for Popper anchoring) and hover/focus handlers directly on the <img>. A
// plain function component here silently drops both, which prevented the tooltip from opening.
const InjectorIcon = forwardRef<HTMLImageElement, Props>(({ type, style, onClick, ...rest }, ref) => {
  return (
    <img
      ref={ref}
      src={buildTenantApiPath(`/api/injectors/${type}/image`)}
      onClick={onClick}
      alt={type}
      style={style}
      {...rest}
    />
  );
});

InjectorIcon.displayName = 'InjectorIcon';

export default InjectorIcon;

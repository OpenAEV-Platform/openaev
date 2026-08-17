import { type CSSProperties, forwardRef } from 'react';

import { buildTenantApiPath } from '../../../../utils/url-helper';

interface Props {
  type: string;
  style: CSSProperties;
  onClick?: () => void;
}

// See InjectorIcon.tsx for why forwardRef + prop spreading is required here.
const CollectorIcon = forwardRef<HTMLImageElement, Props>(({ type, style, onClick, ...rest }, ref) => {
  return (
    <img
      ref={ref}
      src={buildTenantApiPath(`/api/collectors/${type}/image`)}
      onClick={onClick}
      alt={type}
      style={style}
      {...rest}
    />
  );
});

CollectorIcon.displayName = 'CollectorIcon';

export default CollectorIcon;

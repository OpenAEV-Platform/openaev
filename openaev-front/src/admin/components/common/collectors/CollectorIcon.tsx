import { type CSSProperties } from 'react';

import { buildTenantApiPath } from '../../../../utils/url-helper';

interface Props {
  type: string;
  style: CSSProperties;
  onClick?: () => void;
}

const CollectorIcon = ({ type, style, onClick }: Props) => {
  return (
    <img
      src={buildTenantApiPath(`/api/collectors/${type}/image`)}
      onClick={onClick}
      alt={type}
      style={style}
    />
  );
};

export default CollectorIcon;

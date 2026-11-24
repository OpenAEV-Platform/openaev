import { Button, type ButtonProps } from '@filigran/design-system';
import { type CSSProperties, type FunctionComponent } from 'react';
import { Link } from 'react-router';

import { useFormatter } from '../../../../components/i18n';
import type { HealthCheck } from '../../../../utils/api-types';
import healthcheckFixLink, { type HealthcheckOwner } from './healthcheckFixLink';

interface Props {
  healthcheck: HealthCheck;
  owner: HealthcheckOwner;
  priority: ButtonProps['priority'];
  // A reader who cannot change the owner only gets the documentation.
  readOnly?: boolean;
  onClick?: () => void;
  style?: CSSProperties;
}

// The link to where a failing health check gets fixed: a page of the platform, or the documentation.
const HealthcheckFixButton: FunctionComponent<Props> = ({ healthcheck, owner, priority, readOnly = false, onClick, style }) => {
  const { t } = useFormatter();
  const link = healthcheckFixLink(healthcheck.type, owner);
  const label = t(`healthcheck.button.${healthcheck.type}.${healthcheck.detail}`);
  if (link.to && !readOnly) {
    return (
      <Button asChild size="sm" priority={priority} style={style}>
        <Link to={link.to} onClick={onClick}>{label}</Link>
      </Button>
    );
  }
  if (link.href) {
    return (
      <Button asChild size="sm" priority={priority} style={style}>
        <a href={link.href} target="_blank" rel="noopener noreferrer" onClick={onClick}>{label}</a>
      </Button>
    );
  }
  return null;
};

export default HealthcheckFixButton;

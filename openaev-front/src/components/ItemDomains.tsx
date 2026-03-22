import { Chip, Tooltip } from '@mui/material';
import PropTypes from 'prop-types';
import { useMemo } from 'react';
import { makeStyles } from 'tss-react/mui';

import { type DomainHelper } from '../actions/domains/domain-helper';
import { useHelper } from '../store';
import { type Domain } from '../utils/api-types';
import { TO_CLASSIFY } from '../utils/domains/domainUtils';
import { getLabelOfRemainingItems, truncate } from '../utils/String';
import Tag from './common/tag/Tag';

const useStyles = makeStyles()(() => ({
  inline: {
    display: 'inline',
    alignItems: 'center',
    flexWrap: 'nowrap',
    overflow: 'hidden',
  },
}));

interface ItemsDomainsProps {
  domains: Domain[] | string[];
  variant: string;
}

const ItemDomains = ({ domains, variant }: ItemsDomainsProps) => {
  const { classes } = useStyles();

  const allDomains: Domain[] = useHelper((helper: DomainHelper) => {
    return helper.getDomains();
  });

  const resolvedDomains: Domain[] = useMemo(() => {
    if (!domains) return [];
    const isArrayOfIds = typeof domains[0] === 'string';
    if (isArrayOfIds) {
      return allDomains.filter(d =>
        (domains as string[]).includes(d.domain_id),
      );
    }
    return domains as Domain[];
  }, [domains, allDomains]);

  const truncateLimit = variant === 'reduced-view' ? 12 : 20;

  const renderList = () =>
    resolvedDomains
      .filter(d => d.domain_name !== TO_CLASSIFY)
      .map(domain => (
        <span
          key={domain.domain_id}
          style={{
            marginRight: 7,
            display: 'inline-block',
          }}
        >
          <Tag
            label={truncate(domain.domain_name, truncateLimit)}
            color={domain.domain_color}
          />
        </span>
      ));

  const renderSingle = () => {
    const primaryDomain = resolvedDomains[0];
    if (!primaryDomain || primaryDomain.domain_name === TO_CLASSIFY) return null;

    const tooltipLabel = getLabelOfRemainingItems(resolvedDomains, 1, 'domain_name');

    return (
      <>
        <span style={{
          marginRight: 7,
          display: 'inline-block',
        }}
        >
          <Tag
            label={truncate(primaryDomain.domain_name, truncateLimit)}
            color={primaryDomain.domain_color}
          />
        </span>
        {resolvedDomains.length > 1 && (
          <Tooltip title={tooltipLabel}>
            <Chip
              size="small"
              label={`+${resolvedDomains.length - 1}`}
              sx={{
                borderRadius: 1,
                height: 25,
                fontSize: 12,
              }}
            />
          </Tooltip>
        )}
      </>
    );
  };

  return (
    <div className={classes.inline}>
      {variant === 'list' ? renderList() : renderSingle()}
    </div>
  );
};

ItemDomains.propTypes = {
  domains: PropTypes.arrayOf(PropTypes.string),
  variant: PropTypes.string,
};

export default ItemDomains;

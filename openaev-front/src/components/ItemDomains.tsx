import { Chip, Tooltip } from '@mui/material';
import PropTypes from 'prop-types';
import { useMemo } from 'react';
import { makeStyles } from 'tss-react/mui';

import { type DomainHelper } from '../actions/helper';
import { useHelper } from '../store';
import { type Domain } from '../utils/api-types';
import { truncate } from '../utils/String';

const useStyles = makeStyles()(theme => ({
  inline: {
    display: 'inline',
    alignItems: 'center',
    flexWrap: 'nowrap',
    overflow: 'hidden',
  },
  domainChip: {
    height: theme.spacing(3),
    fontSize: theme.typography.pxToRem(12),
    marginRight: theme.spacing(1),
    borderRadius: theme.shape.borderRadius,
  },
  domainChipInList: {
    fontSize: theme.typography.pxToRem(12),
    height: theme.spacing(2.5),
    float: 'left',
    textTransform: 'uppercase',
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

  let truncateLimit = 20;
  let style = classes.domainChip;

  if (variant === 'list') {
    style = `${classes.domainChip} ${classes.domainChipInList}`;
  }
  if (variant === 'reduced-view') {
    style = `${classes.domainChip} ${classes.domainChipInList}`;
    truncateLimit = 12;
  }

  const renderList = () =>
    resolvedDomains
      .filter(d => d.domain_name !== 'To classify')
      .map(domain => (
        <Tooltip key={domain.domain_id} title={domain.domain_name}>
          <Chip
            variant="outlined"
            classes={{ root: style }}
            label={truncate(domain.domain_name, truncateLimit)}
            style={{
              color: domain.domain_color,
              borderColor: domain.domain_color,
              backgroundColor: 'transparent',
            }}
          />
        </Tooltip>
      ));

  const renderSingle = () => {
    const primaryDomain = resolvedDomains[0];
    if (!primaryDomain || primaryDomain.domain_name === 'To classify') return null;

    return (
      <>
        <Tooltip title={primaryDomain.domain_name}>
          <Chip
            variant="outlined"
            classes={{ root: style }}
            label={truncate(primaryDomain.domain_name, truncateLimit)}
            style={{
              color: primaryDomain.domain_color,
              borderColor: primaryDomain.domain_color,
              backgroundColor: 'transparent',
            }}
          />
        </Tooltip>
        {resolvedDomains.length > 1 && (
          <Tooltip title={resolvedDomains.filter((_, index) => index !== 0)
            .map(d => d.domain_name).join(' | ')}
          >
            <Chip
              variant="outlined"
              classes={{ root: style }}
              label={`+${domains.length - 1}`}
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

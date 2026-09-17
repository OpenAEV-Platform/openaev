import { Chip, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import PropTypes from 'prop-types';
import { useMemo } from 'react';
import { makeStyles } from 'tss-react/mui';

import { type DomainHelper } from '../actions/domains/domain-helper';
import { useHelper } from '../store';
import { type Domain } from '../utils/api-types';
import { getIconByDomain } from '../utils/domains/domainIcons';
import { TO_CLASSIFY } from '../utils/domains/domainUtils';
import { getLabelOfRemainingItems, truncate } from '../utils/String';

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
  if (variant === 'reduced-view') {
    truncateLimit = 12;
  }

  const renderList = () =>
    resolvedDomains
      .filter(d => d.domain_name !== TO_CLASSIFY)
      .map(domain => (
        <Tooltip key={domain.domain_id}>
          <TooltipTrigger asChild>
            <Chip
              startIcon={getIconByDomain(domain.domain_name, {
                fontSize: 14,
                color: domain.domain_color,
              })}
              label={truncate(domain.domain_name, truncateLimit) ?? ''}
              color={domain.domain_color}
            />
          </TooltipTrigger>
          {domain.domain_name && <TooltipContent>{domain.domain_name}</TooltipContent>}
        </Tooltip>
      ));

  const renderSingle = () => {
    const primaryDomain = resolvedDomains[0];
    if (!primaryDomain || primaryDomain.domain_name === TO_CLASSIFY) return null;

    const tooltipLabel = getLabelOfRemainingItems(resolvedDomains, 1, 'domain_name');

    return (
      <>
        <Tooltip>
          <TooltipTrigger asChild>
            <Chip
              startIcon={getIconByDomain(primaryDomain.domain_name, {
                fontSize: 14,
                color: primaryDomain.domain_color,
              })}
              label={truncate(primaryDomain.domain_name, truncateLimit) ?? ''}
              color={primaryDomain.domain_color}
            />
          </TooltipTrigger>
          {primaryDomain.domain_name && <TooltipContent>{primaryDomain.domain_name}</TooltipContent>}
        </Tooltip>

        {resolvedDomains.length > 1 && (
          <Tooltip>
            <TooltipTrigger asChild>
              <Chip label={`+${resolvedDomains.length - 1}`} />
            </TooltipTrigger>
            {tooltipLabel && <TooltipContent>{tooltipLabel}</TooltipContent>}
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

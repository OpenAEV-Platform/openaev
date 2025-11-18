import { Chip, Tooltip } from '@mui/material';
import PropTypes from 'prop-types';
import { useMemo } from 'react';
import { makeStyles } from 'tss-react/mui';

import { type DomainHelper } from '../actions/helper';
import { useHelper } from '../store';
import { type Domain } from '../utils/api-types';
import { useFormatter } from './i18n';

const useStyles = makeStyles()(() => ({
  inline: {
    display: 'inline',
    alignItems: 'center',
    flexWrap: 'nowrap',
    overflow: 'hidden',
  },
  domainChip: {
    height: 25,
    fontSize: 12,
    margin: '0 7px 0 0',
    borderRadius: 4,
  },
  domainChipInList: {
    fontSize: 12,
    height: 20,
    float: 'left',
    textTransform: 'uppercase',
  },
}));

interface ItemsDomainsProps {
  domains: string[];
  variant: string;
}

const ItemDomains = ({ domains, variant }: ItemsDomainsProps) => {
  const { t } = useFormatter();
  const { classes } = useStyles();

  const allDomains: Domain[] = useHelper((helper: DomainHelper) => {
    return helper.getDomains();
  });

  const resolvedDomains = useMemo(() => allDomains.filter(domain => (domains ?? []).includes(domain.domain_id)), [allDomains, domains]);

  const visibleDomain = resolvedDomains[0];
  const remainingCount = resolvedDomains.length - 1;

  let style = classes.domainChip;
  if (variant === 'reduced-view') {
    style = `${classes.domainChip} ${classes.domainChipInList}`;
  }

  return (
    <div className={classes.inline}>
      {visibleDomain ? (
        <>
          <Tooltip title={visibleDomain.domain_name}>
            <Chip
              variant="outlined"
              classes={{ root: style }}
              label={visibleDomain.domain_name}
              style={{
                color: visibleDomain.domain_color,
                borderColor: visibleDomain.domain_color,
                backgroundColor: 'transparent',
              }}
            />
          </Tooltip>
          {remainingCount > 0 && (
            <Tooltip title={t('Additional domains')}>
              <Chip
                variant="outlined"
                classes={{ root: style }}
                label={`+${remainingCount}`}
              />
            </Tooltip>
          )}
        </>
      ) : null}
    </div>
  );
};

ItemDomains.propTypes = {
  domains: PropTypes.arrayOf(PropTypes.string),
  variant: PropTypes.string,
};

export default ItemDomains;

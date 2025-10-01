import { Box } from '@mui/material';
import { Fragment, type FunctionComponent, useContext, useEffect } from 'react';
import { makeStyles } from 'tss-react/mui';

import { type Filter, type PropertySchemaDTO } from '../../../../utils/api-types';
import { type Option } from '../../../../utils/Option';
import { useFormatter } from '../../../i18n';
import { FilterContext } from './context';
import { convertOperatorToIcon } from './FilterUtils';
import useRetrieveOptions from './useRetrieveOptions';

const useStyles = makeStyles()(theme => ({
  mode: {
    display: 'inline-block',
    height: '100%',
    backgroundColor: theme.palette.action?.selected,
    margin: '0 4px',
    padding: '0 4px',
  },
  container: {
    gap: '4px',
    display: 'flex',
    alignItems: 'center',
    lineHeight: '32px',
    maxWidth: '400px',
  },
  label: {
    'cursor': 'pointer',
    '&:hover': { textDecorationLine: 'underline' },
  },
}));

interface Props {
  filter: Filter;
  propertySchema?: PropertySchemaDTO;
  isTooltip?: boolean;
  handleOpen?: () => void;
}

const FilterChipValues: FunctionComponent<Props> = ({
  filter,
  propertySchema,
  isTooltip = false,
  handleOpen,
}) => {
  // Standard hooks
  const { t, fldt } = useFormatter();
  const { classes, cx } = useStyles();

  const { options, searchOptions } = useRetrieveOptions();
  const { defaultValues } = useContext(FilterContext);
  useEffect(() => {
    if (filter.values) {
      searchOptions(filter.key, filter.values, { defaultValues: defaultValues.get(filter.key) });
    }
  }, [filter]);

  const i18nMode = (mode: Filter['mode']) => (
    <div className={classes.mode}>
      {t(mode === 'and' ? 'AND' : 'OR')}
    </div>
  );

  const toValues = (opts: Option[], mode: Filter['mode']) => opts.map((o, idx) => (
    <Fragment key={o.id}>
      {idx > 0 && i18nMode(mode)}
      <span>
        {' '}
        {propertySchema?.schema_property_type.includes('instant') || !o.label ? (o.label) : t(o.label)}
      </span>
    </Fragment>
  ));

  const operator = filter.operator ?? 'eq';
  const isOperatorNil = ['empty', 'not_empty'].includes(operator);
  if (isOperatorNil) {
    return (
      <>
        <strong
          className={cx({ [classes.label]: !!handleOpen })}
          onClick={handleOpen}
        >
          {t(filter.key)}
        </strong>
        {' '}
        <span>
          {operator === 'empty' ? t('is empty') : t('is not empty')}
        </span>
      </>
    );
  }

  if (isTooltip) {
    let str = '';
    options.forEach((o, idx) => {
      if (idx > 0) {
        str = `${str} ${t('OR')}`;
      }
      if (propertySchema?.schema_property_type.includes('instant')) {
        str = `${str} ${o.label ? fldt(o.label) : o.label}`;
      } else {
        str = `${str} ${o.label ? t(o.label) : o.label}`;
      }
    });
    return str;
  }

  return (
    (
      <span className={classes.container}>
        <strong
          className={cx({ [classes.label]: !!handleOpen })}
          onClick={handleOpen}
        >
          {t(filter.key)}
          {convertOperatorToIcon(t, filter.operator)}
        </strong>
        {' '}
        <Box sx={{
          display: 'flex',
          flexDirection: 'row',
          overflow: 'hidden',
        }}
        >
          {toValues(options, filter.mode)}
        </Box>
      </span>
    )
  );
};

export default FilterChipValues;

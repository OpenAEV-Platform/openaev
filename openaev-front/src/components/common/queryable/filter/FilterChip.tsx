import { Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { Cancel } from '@mui/icons-material';
// fds:keep-mui deferred to the filter bar wave (LIBRARY-FEEDBACK #54: the filter chip edits its and/or from the label)
import { Chip } from '@mui/material';
import * as R from 'ramda';
import { type FunctionComponent, useEffect, useRef, useState } from 'react';

import { type Filter, type PropertySchemaDTO } from '../../../../utils/api-types';
import { useFormatter } from '../../../i18n';
import FilterChipPopover from './FilterChipPopover';
import FilterChipValues from './FilterChipValues';
import { type FilterHelpers } from './FilterHelpers';

interface Props {
  filter: Filter;
  helpers: FilterHelpers;
  propertySchema: PropertySchemaDTO;
  pristine: boolean;
  contextId?: string;
}

const FilterChip: FunctionComponent<Props> = ({
  filter,
  helpers,
  propertySchema,
  pristine,
  contextId,
}) => {
  const { t } = useFormatter();

  const chipRef = useRef<HTMLDivElement>(null);
  const [open, setOpen] = useState(!pristine);
  const handleOpen = () => setOpen(true);
  const handleClose = () => setOpen(false);

  const handleRemoveFilter = () => {
    if (helpers) {
      helpers.handleRemoveFilterById(filter.id);
    }
  };

  const isEmpty = (values?: string[]) => {
    return R.isEmpty(values) || values?.some(v => R.isEmpty(v));
  };

  const chipVariant = isEmpty(filter.values) && !['empty', 'not_empty'].includes(filter.operator ?? 'eq')
    ? 'outlined'
    : 'filled';

  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);

  useEffect(() => {
    if (chipRef.current) {
      setAnchorEl(chipRef.current);
    }
  }, [chipRef.current]);
  return (
    <>
      <Chip
        variant={chipVariant}
        label={(
          <Tooltip>
            <TooltipTrigger asChild>
              <span>
                <FilterChipValues
                  filter={filter}
                  propertySchema={propertySchema}
                  handleOpen={handleOpen}
                  contextId={contextId}
                  helpers={helpers}
                />
              </span>
            </TooltipTrigger>
            <TooltipContent>
              <FilterChipValues
                filter={filter}
                propertySchema={propertySchema}
                isTooltip
                handleOpen={handleOpen}
                contextId={contextId}
              />
            </TooltipContent>
          </Tooltip>
        )}
        onDelete={handleRemoveFilter}
        deleteIcon={(
          <Tooltip>
            <TooltipTrigger asChild>
              <Cancel />
            </TooltipTrigger>
            <TooltipContent>{t('Clear all')}</TooltipContent>
          </Tooltip>
        )}
        sx={{ borderRadius: 1 }}
        ref={chipRef}
      />

      {anchorEl && (
        <FilterChipPopover
          filter={filter}
          helpers={helpers}
          open={open}
          onClose={handleClose}
          anchorEl={chipRef.current!}
          propertySchema={propertySchema}
          contextId={contextId}
        />
      )}
    </>
  );
};
export default FilterChip;

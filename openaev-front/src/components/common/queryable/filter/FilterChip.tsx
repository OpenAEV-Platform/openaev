import { Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { Cancel } from '@mui/icons-material';
// fds:keep-mui deferred to the filter bar wave (LIBRARY-FEEDBACK #54: the filter chip edits its and/or from the label)
import { Chip, type SvgIconProps } from '@mui/material';
import * as R from 'ramda';
import { forwardRef, type FunctionComponent, useEffect, useRef, useState } from 'react';

import { type Filter, type PropertySchemaDTO } from '../../../../utils/api-types';
import { useFormatter } from '../../../i18n';
import FilterChipPopover from './FilterChipPopover';
import FilterChipValues from './FilterChipValues';
import { type FilterHelpers } from './FilterHelpers';

/**
 * The chip's clear cross. MUI clones `deleteIcon` to inject its own class and its
 * `onDelete` handler, so the element it is given has to FORWARD what it receives:
 * handing it a tooltip component directly swallowed both — the cross stopped
 * deleting, and it lost the class the spacing below hangs on.
 */
const ClearFilterIcon = forwardRef<SVGSVGElement, SvgIconProps>((props, ref) => {
  const { t } = useFormatter();
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <Cancel {...props} ref={ref} />
      </TooltipTrigger>
      <TooltipContent>{t('Clear all')}</TooltipContent>
    </Tooltip>
  );
});
ClearFilterIcon.displayName = 'ClearFilterIcon';

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
        deleteIcon={<ClearFilterIcon />}
        sx={{
          'borderRadius': 1,
          // 8px on BOTH sides of the clear cross: the label's own padding on its
          // left, the icon's margin on its right.
          '& .MuiChip-label': { paddingRight: 1 },
          '& .MuiChip-deleteIcon': { margin: '0 8px 0 0' },
        }}
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

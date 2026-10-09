import { TooltipProvider } from '@filigran/design-system';
import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, fireEvent, render } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import FilterChip from '../../../../../components/common/queryable/filter/FilterChip';
import { type FilterHelpers } from '../../../../../components/common/queryable/filter/FilterHelpers';
import { defineAbility } from '../../../../../utils/permissions/ability';
import { AbilityProvider } from '../../../../../utils/permissions/permissionsContext';

vi.mock('../../../../../components/i18n', () => ({ useFormatter: () => ({ t: (s: string) => s }) }));
vi.mock('../../../../../components/common/queryable/filter/FilterChipPopover', () => ({ default: () => null }));

const helpers = { handleRemoveFilterById: vi.fn() } as unknown as FilterHelpers;

const filter = {
  id: 'f-1',
  key: 'organization_name',
  mode: 'and' as const,
  operator: 'empty' as const,
  values: [],
};

const propertySchema = {
  schema_property_name: 'organization_name',
  schema_property_label: 'Name',
  schema_property_type: 'string',
} as never;

// MUI clones `deleteIcon` to inject its own class and the `onDelete` handler: an element that
// does not forward what it receives silently stops deleting, and no gate sees it.
describe('FilterChip', () => {
  afterEach(cleanup);

  it('removes its filter when the clear cross is clicked', () => {
    render(
      <ThemeProvider theme={createTheme()}>
        <TooltipProvider>
          <AbilityProvider value={defineAbility([], {}, false)}>
            <FilterChip filter={filter} helpers={helpers} propertySchema={propertySchema} pristine />
          </AbilityProvider>
        </TooltipProvider>
      </ThemeProvider>,
    );
    const cross = document.querySelector('.MuiChip-deleteIcon');
    expect(cross).toBeTruthy();
    fireEvent.click(cross!);
    expect(helpers.handleRemoveFilterById).toHaveBeenCalledWith('f-1');
  });
});

import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, render, screen } from '@testing-library/react';
import { type ReactNode } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import FilterChipValues from '../../../../../components/common/queryable/filter/FilterChipValues';
import { type Filter, type PropertySchemaDTO } from '../../../../../utils/api-types';

const ISO_DATE = '2026-09-18T22:00:00.000Z';

vi.mock('../../../../../components/i18n', () => ({
  useFormatter: () => ({
    t: (s: string) => `t:${s}`,
    fldt: (s: string) => `date:${s}`,
  }),
}));

let mockOptions: {
  id: string;
  label: string;
}[] = [];
vi.mock('../../../../../components/common/queryable/filter/useRetrieveOptions', () => ({
  default: () => ({
    options: mockOptions,
    searchOptions: vi.fn(),
  }),
}));

const schema = (type: string): PropertySchemaDTO => ({
  schema_property_entity: 'MarkingDefinition',
  schema_property_label: 'Creation date',
  schema_property_name: 'marking_definition_created_at',
  schema_property_type: type,
});

const filter: Filter = {
  id: 'f1',
  key: 'marking_definition_created_at',
  mode: 'or',
  operator: 'gt',
  values: [ISO_DATE],
};

const wrapper = ({ children }: { children: ReactNode }) => (
  <ThemeProvider theme={createTheme()}>{children}</ThemeProvider>
);

describe('FilterChipValues', () => {
  afterEach(cleanup);

  it('formats a date value in the chip instead of showing the raw UTC string', () => {
    mockOptions = [{
      id: ISO_DATE,
      label: ISO_DATE,
    }];
    render(<FilterChipValues filter={filter} propertySchema={schema('instant')} />, { wrapper });
    expect(screen.getByText(`date:${ISO_DATE}`)).toBeTruthy();
    expect(screen.queryByText(ISO_DATE)).toBeNull();
  });

  it('formats the chip and its tooltip the same way', () => {
    mockOptions = [{
      id: ISO_DATE,
      label: ISO_DATE,
    }];
    const { container } = render(
      <FilterChipValues filter={filter} propertySchema={schema('instant')} isTooltip />,
      { wrapper },
    );
    expect(container.textContent?.trim()).toBe(`date:${ISO_DATE}`);
  });

  it('still translates non-date values', () => {
    mockOptions = [{
      id: 'TLP',
      label: 'TLP',
    }];
    render(
      <FilterChipValues
        filter={{
          ...filter,
          key: 'marking_definition_type',
          values: ['TLP'],
        }}
        propertySchema={schema('string')}
      />,
      { wrapper },
    );
    expect(screen.getByText('t:TLP')).toBeTruthy();
  });
});

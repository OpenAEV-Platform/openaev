import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, render, screen } from '@testing-library/react';
import { type ReactNode } from 'react';
import { IntlProvider } from 'react-intl';
import { Provider } from 'react-redux';
import { afterEach, describe, expect, it } from 'vitest';

import { PermissionsContext, type PermissionsContextType } from '../../../../../../admin/components/common/Context';
import { type ExpectationInput } from '../../../../../../admin/components/common/injects/expectations/Expectation';
import InjectExpectations from '../../../../../../admin/components/common/injects/expectations/InjectExpectations';
import { store } from '../../../../../../store';
import { defineAbility } from '../../../../../../utils/permissions/ability';
import { AbilityProvider } from '../../../../../../utils/permissions/permissionsContext';
import { INHERITED_CONTEXT } from '../../../../../../utils/permissions/types';

// -- TEST DATA --

const makeExpectation = (overrides: Partial<ExpectationInput> = {}): ExpectationInput => ({
  expectation_type: 'DETECTION',
  expectation_name: 'My detection',
  expectation_score: 100,
  expectation_expectation_group: false,
  expectation_expiration_time: 3600,
  expectation_is_predefined: false,
  ...overrides,
});

const SINGLE_SELECT_DETECTION = makeExpectation({
  expectation_type: 'DETECTION',
  expectation_name: 'Detection',
  expectation_is_multi_selectable: false,
});
const MULTI_SELECT_MANUAL = makeExpectation({
  expectation_type: 'MANUAL',
  expectation_name: 'Manual check',
  expectation_is_multi_selectable: true,
});
const SINGLE_SELECT_PREVENTION = makeExpectation({
  expectation_type: 'PREVENTION',
  expectation_name: 'Prevention',
  expectation_is_multi_selectable: false,
});

// -- HELPERS --

const theme = createTheme();
/** Ability that grants canManage=true on all to bypass permission checks */
const fullAbility = defineAbility(['BYPASS'], {}, false);
/** Ability with no grants — forces permission checks to rely only on PermissionsContext */
const emptyAbility = defineAbility([], {}, false);

const permissionsCanManage: PermissionsContextType = {
  permissions: {
    readOnly: false,
    canManage: true,
    canAccess: true,
    canLaunch: false,
    canDelete: false,
    isRunning: false,
  },
  inherited_context: INHERITED_CONTEXT.NONE,
};

const renderInjectExpectations = (
  props: {
    expectationDatas?: ExpectationInput[];
    availableExpectations?: ExpectationInput[];
  },
  permissionsOverride: PermissionsContextType = permissionsCanManage,
  ability = fullAbility,
) => {
  const wrapper = ({ children }: { children: ReactNode }) => (
    <Provider store={store}>
      <ThemeProvider theme={theme}>
        <IntlProvider locale="en" messages={{}} defaultLocale="en" onError={() => {}}>
          <AbilityProvider value={ability}>
            <PermissionsContext.Provider value={permissionsOverride}>
              {children}
            </PermissionsContext.Provider>
          </AbilityProvider>
        </IntlProvider>
      </ThemeProvider>
    </Provider>
  );

  return render(
    <InjectExpectations
      expectationDatas={props.expectationDatas ?? []}
      handleExpectations={() => {}}
      availableExpectations={props.availableExpectations ?? []}
    />,
    { wrapper },
  );
};

// -- TESTS --

describe('InjectExpectations', () => {
  afterEach(cleanup);

  describe('Add expectations button visibility', () => {
    it('shows the "Add expectations" button when available expectations exist', () => {
      renderInjectExpectations({ availableExpectations: [SINGLE_SELECT_DETECTION] });

      expect(screen.getByText('Add expectations')).toBeDefined();
    });

    it('hides the "Add expectations" button when there are no addable expectations', () => {
      // SINGLE_SELECT_DETECTION is already added as expectationData -> it is filtered out
      renderInjectExpectations({
        expectationDatas: [SINGLE_SELECT_DETECTION],
        availableExpectations: [SINGLE_SELECT_DETECTION],
      });

      expect(screen.queryByText('Add expectations')).toBeNull();
    });
  });

  describe('isMultiSelectable=false - single selection', () => {
    it('removes the type from the add list once it is already in the inject', () => {
      // DETECTION is single-select and already added -> should no longer be offered
      renderInjectExpectations({
        expectationDatas: [SINGLE_SELECT_DETECTION],
        availableExpectations: [SINGLE_SELECT_DETECTION, MULTI_SELECT_MANUAL],
      });

      // Only MANUAL remains -> button still visible
      expect(screen.getByText('Add expectations')).toBeDefined();
    });

    it('keeps a second single-select type available when only the first is already added', () => {
      renderInjectExpectations({
        expectationDatas: [SINGLE_SELECT_DETECTION],
        availableExpectations: [SINGLE_SELECT_DETECTION, SINGLE_SELECT_PREVENTION],
      });

      // PREVENTION is not yet added -> button still visible
      expect(screen.getByText('Add expectations')).toBeDefined();
    });

    it('hides the button when all single-select types are already in the inject', () => {
      renderInjectExpectations({
        expectationDatas: [SINGLE_SELECT_DETECTION, SINGLE_SELECT_PREVENTION],
        availableExpectations: [SINGLE_SELECT_DETECTION, SINGLE_SELECT_PREVENTION],
      });

      expect(screen.queryByText('Add expectations')).toBeNull();
    });
  });

  describe('isMultiSelectable=true - multiple selections', () => {
    it('keeps the type available even after it has been added once', () => {
      renderInjectExpectations({
        expectationDatas: [MULTI_SELECT_MANUAL],
        availableExpectations: [MULTI_SELECT_MANUAL],
      });

      // MANUAL is multi-selectable -> still offered after first addition
      expect(screen.getByText('Add expectations')).toBeDefined();
    });

    it('keeps the type available after multiple additions', () => {
      const secondManual = makeExpectation({
        expectation_type: 'MANUAL',
        expectation_name: 'Manual check 2',
        expectation_is_multi_selectable: true,
      });
      renderInjectExpectations({
        expectationDatas: [MULTI_SELECT_MANUAL, secondManual],
        availableExpectations: [MULTI_SELECT_MANUAL],
      });

      expect(screen.getByText('Add expectations')).toBeDefined();
    });
  });

  describe('isMultiSelectable fallback (undefined)', () => {
    it('treats undefined isMultiSelectable as false (single-select) - hides button when type already added', () => {
      const noFlag = makeExpectation({
        expectation_type: 'DETECTION',
        expectation_name: 'Detection',
      });
      // expectation_is_multi_selectable is undefined -> fallback to false (single-select)
      renderInjectExpectations({
        expectationDatas: [noFlag],
        availableExpectations: [noFlag],
      });

      expect(screen.queryByText('Add expectations')).toBeNull();
    });
  });

  describe('Read-only mode', () => {
    it('never shows the "Add expectations" button in read-only mode', () => {
      renderInjectExpectations(
        { availableExpectations: [MULTI_SELECT_MANUAL] },
        {
          ...permissionsCanManage,
          permissions: {
            ...permissionsCanManage.permissions,
            readOnly: true,
            canManage: false,
          },
        },
        emptyAbility,
      );

      expect(screen.queryByText('Add expectations')).toBeNull();
    });
  });
});

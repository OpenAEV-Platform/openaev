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
import { AbilityContext } from '../../../../../../utils/permissions/permissionsContext';
import { INHERITED_CONTEXT } from '../../../../../../utils/permissions/types';

// -- TEST DATA --

const makeExpectation = (overrides: Partial<ExpectationInput> = {}): ExpectationInput => ({
  expectation_type: 'DETECTION',
  expectation_name: 'My detection',
  expectation_score: 100,
  expectation_expectation_group: false,
  expectation_expiration_time: 3600,
  ...overrides,
});

const LIMITED_DETECTION = makeExpectation({
  expectation_type: 'DETECTION',
  expectation_name: 'Detection',
  expectation_is_limited: true,
});
const UNLIMITED_MANUAL = makeExpectation({
  expectation_type: 'MANUAL',
  expectation_name: 'Manual check',
  expectation_is_limited: false,
});
const LIMITED_PREVENTION = makeExpectation({
  expectation_type: 'PREVENTION',
  expectation_name: 'Prevention',
  expectation_is_limited: true,
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
    predefinedExpectations?: ExpectationInput[];
  },
  permissionsOverride: PermissionsContextType = permissionsCanManage,
  ability = fullAbility,
) => {
  const wrapper = ({ children }: { children: ReactNode }) => (
    <Provider store={store}>
      <ThemeProvider theme={theme}>
        <IntlProvider locale="en" messages={{}} defaultLocale="en" onError={() => {}}>
          <AbilityContext.Provider value={ability}>
            <PermissionsContext.Provider value={permissionsOverride}>
              {children}
            </PermissionsContext.Provider>
          </AbilityContext.Provider>
        </IntlProvider>
      </ThemeProvider>
    </Provider>
  );

  return render(
    <InjectExpectations
      expectationDatas={props.expectationDatas ?? []}
      handleExpectations={() => {}}
      availableExpectations={props.availableExpectations ?? []}
      predefinedExpectations={props.predefinedExpectations ?? []}
    />,
    { wrapper },
  );
};

// -- TESTS --

describe('InjectExpectations', () => {
  afterEach(cleanup);

  describe('Add expectations button visibility', () => {
    it('shows the "Add expectations" button when available expectations exist', () => {
      renderInjectExpectations({ availableExpectations: [LIMITED_DETECTION] });

      expect(screen.getByText('Add expectations')).toBeDefined();
    });

    it('hides the "Add expectations" button when there are no addable expectations', () => {
      // LIMITED_DETECTION is already added as expectationData → it is filtered out
      renderInjectExpectations({
        expectationDatas: [LIMITED_DETECTION],
        availableExpectations: [LIMITED_DETECTION],
      });

      expect(screen.queryByText('Add expectations')).toBeNull();
    });
  });

  describe('isLimited=true — limited to one selection', () => {
    it('removes the type from the add list once it is already in the inject', () => {
      // DETECTION is limited and already added → should no longer be offered
      renderInjectExpectations({
        expectationDatas: [LIMITED_DETECTION],
        availableExpectations: [LIMITED_DETECTION, UNLIMITED_MANUAL],
      });

      // Only MANUAL remains → button still visible
      expect(screen.getByText('Add expectations')).toBeDefined();
    });

    it('keeps a second limited type available when only the first is already added', () => {
      renderInjectExpectations({
        expectationDatas: [LIMITED_DETECTION],
        availableExpectations: [LIMITED_DETECTION, LIMITED_PREVENTION],
      });

      // PREVENTION is not yet added → button still visible
      expect(screen.getByText('Add expectations')).toBeDefined();
    });

    it('hides the button when all limited types are already in the inject', () => {
      renderInjectExpectations({
        expectationDatas: [LIMITED_DETECTION, LIMITED_PREVENTION],
        availableExpectations: [LIMITED_DETECTION, LIMITED_PREVENTION],
      });

      expect(screen.queryByText('Add expectations')).toBeNull();
    });
  });

  describe('isLimited=false — unlimited selections', () => {
    it('keeps the type available even after it has been added once', () => {
      renderInjectExpectations({
        expectationDatas: [UNLIMITED_MANUAL],
        availableExpectations: [UNLIMITED_MANUAL],
      });

      // MANUAL is unlimited → still offered after first addition
      expect(screen.getByText('Add expectations')).toBeDefined();
    });

    it('keeps the type available after multiple additions', () => {
      const secondManual = makeExpectation({
        expectation_type: 'MANUAL',
        expectation_name: 'Manual check 2',
        expectation_is_limited: false,
      });
      renderInjectExpectations({
        expectationDatas: [UNLIMITED_MANUAL, secondManual],
        availableExpectations: [UNLIMITED_MANUAL],
      });

      expect(screen.getByText('Add expectations')).toBeDefined();
    });
  });

  describe('isLimited fallback (undefined)', () => {
    it('treats undefined isLimited as limited (true) — hides button when type already added', () => {
      const noFlag = makeExpectation({
        expectation_type: 'DETECTION',
        expectation_name: 'Detection',
      });
      // expectation_is_limited is undefined → fallback to true (limited)
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
        { availableExpectations: [UNLIMITED_MANUAL] },
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

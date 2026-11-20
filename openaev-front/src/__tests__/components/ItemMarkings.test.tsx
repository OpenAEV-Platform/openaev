import '@testing-library/jest-dom/vitest';

import { TooltipProvider } from '@filigran/design-system';
import { cleanup, render, screen } from '@testing-library/react';
import { normalize } from 'normalizr';
import { Provider } from 'react-redux';
import { createStore } from 'redux';
import { afterEach, describe, expect, it } from 'vitest';

import { arrayOfMarkingDefinitions } from '../../actions/Schema';
import ItemMarkings from '../../components/ItemMarkings';
import * as Constants from '../../constants/ActionTypes';
import createRootReducer from '../../reducers/Root';
import { initialState } from '../../store';
import { type MarkingDefinitionOutput } from '../../utils/api-types';

const marking = (id: string, type: string, definition: string, order: number): MarkingDefinitionOutput => ({
  marking_definition_id: id,
  marking_definition_type: type,
  marking_definition_definition: definition,
  marking_definition_color: '#ffffff',
  marking_definition_order: order,
  marking_definition_protected: false,
}) as MarkingDefinitionOutput;

const DEFINITIONS = [
  marking('tlp-clear', 'TLP', 'TLP:CLEAR', 1),
  marking('tlp-red', 'TLP', 'TLP:RED', 5),
  marking('pap-amber', 'PAP', 'PAP:AMBER', 3),
];

const renderMarkings = (markingIds: string[] | undefined, limit?: number) => {
  const store = createStore(createRootReducer(), initialState);
  store.dispatch({
    type: Constants.DATA_FETCH_SUCCESS,
    payload: normalize(DEFINITIONS, arrayOfMarkingDefinitions),
  });
  return render(
    <Provider store={store}>
      <TooltipProvider>
        <ItemMarkings markingIds={markingIds} limit={limit} />
      </TooltipProvider>
    </Provider>,
  );
};

describe('ItemMarkings', () => {
  afterEach(() => {
    cleanup();
  });

  it('given marking ids loaded in the store should show the highest marking of each type, most restrictive first', () => {
    // Act
    renderMarkings(['tlp-clear', 'pap-amber', 'tlp-red']);

    // Assert
    const labels = screen.getAllByText(/^(TLP|PAP):/).map(chip => chip.textContent);
    expect(labels).toEqual(['TLP:RED', 'PAP:AMBER']);
    expect(screen.queryByText('TLP:CLEAR')).not.toBeInTheDocument();
  });

  it('given more markings than the limit should show the first ones and count the others', () => {
    // Act
    renderMarkings(['tlp-red', 'pap-amber'], 1);

    // Assert
    expect(screen.getByText('TLP:RED')).toBeInTheDocument();
    expect(screen.queryByText('PAP:AMBER')).not.toBeInTheDocument();
    expect(screen.getByText('+1')).toBeInTheDocument();
  });

  it.each([
    ['ids missing from the store', ['unknown-marking']],
    ['no marking ids', undefined],
  ])('given %s should show a dash', (_, markingIds) => {
    // Act
    renderMarkings(markingIds);

    // Assert
    expect(screen.getByText('-')).toBeInTheDocument();
  });
});

import { createTheme, ThemeProvider } from '@mui/material/styles';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { type ReactNode } from 'react';
import { afterEach, describe, expect, it } from 'vitest';

import GraphCardTooltip from '../../../../../../admin/components/chaining/logic/logic-graph/GraphCardTooltip';

const wrapper = ({ children }: { children: ReactNode }) => (
  <ThemeProvider theme={createTheme()}>{children}</ThemeProvider>
);

const TOOLTIP_BODY = 'Rich tooltip body';
const CARD_BODY = 'Card body';
const MENU = 'menu';

// The nested button mirrors the cards' row-menu / connect-handle children, which stopPropagation
// on pointerdown in the BUBBLE phase to avoid starting a node drag: the wrapper's capture-phase
// dismiss must fire anyway.
const card = (dismissKey?: unknown) => (
  <GraphCardTooltip title={<div>{TOOLTIP_BODY}</div>} dismissKey={dismissKey}>
    <div>
      {CARD_BODY}
      <button type="button" onPointerDown={e => e.stopPropagation()}>{MENU}</button>
    </div>
  </GraphCardTooltip>
);

// The tooltip opens after its enterDelay; findByText waits for it.
const openTooltip = async () => {
  fireEvent.mouseOver(screen.getByText(CARD_BODY));
  return screen.findByText(TOOLTIP_BODY);
};

// These pin the two force-close paths that fix the reported "tooltip stays open" defect: an
// uncontrolled tooltip inside the pan/zoom canvas was routinely robbed of its mouseleave/blur
// close by presses (drawer/menu opening) and by structural relayouts sliding the card out from
// under a stationary cursor.
describe('GraphCardTooltip force-close paths', () => {
  afterEach(cleanup);

  it('given_anOpenTooltip_should_closeOnAPointerPressEvenFromAStopPropagationChild', async () => {
    // Arrange
    render(card('sig-1'), { wrapper });
    expect(await openTooltip()).toBeTruthy();

    // Act: press the nested control whose bubble-phase handler stops propagation - the dismiss
    // runs in the CAPTURE phase, so it must fire regardless.
    fireEvent.pointerDown(screen.getByText(MENU));

    // Assert
    await waitFor(() => expect(screen.queryByText(TOOLTIP_BODY)).toBeNull());
  });

  it('given_anOpenTooltip_should_closeWhenTheDismissKeyChanges', async () => {
    // Arrange: the graph passes its layout fitSignature as the dismissKey.
    const { rerender } = render(card('sig-1'), { wrapper });
    expect(await openTooltip()).toBeTruthy();

    // Act: a structural relayout changes the signature (a poll authored/removed a step).
    rerender(card('sig-2'));

    // Assert
    await waitFor(() => expect(screen.queryByText(TOOLTIP_BODY)).toBeNull());
  });

  it('given_anOpenTooltip_should_stayOpenAcrossAPollRerenderWithTheSameDismissKey', async () => {
    // Arrange
    const { rerender } = render(card('sig-1'), { wrapper });
    expect(await openTooltip()).toBeTruthy();

    // Act: the autonomous poll re-renders the canvas every few seconds WITHOUT changing the
    // layout signature - the tooltip must not blink shut under a hovering cursor.
    rerender(card('sig-1'));
    await new Promise(resolve => setTimeout(resolve, 250));

    // Assert
    expect(screen.getByText(TOOLTIP_BODY)).toBeTruthy();
  });

  it('given_anOpenTooltip_should_closeOnWheelZoom', async () => {
    // Arrange
    render(card('sig-1'), { wrapper });
    expect(await openTooltip()).toBeTruthy();

    // Act: the operator zooms the pan/zoom canvas. The canvas is overflow:hidden and preventDefaults
    // the wheel, so Popper never repositions - the capture-phase wheel listener must force-close the
    // card instead of letting it detach and float over the graph.
    fireEvent.wheel(window, { deltaY: -120 });

    // Assert
    await waitFor(() => expect(screen.queryByText(TOOLTIP_BODY)).toBeNull());
  });
});

import '@testing-library/jest-dom/vitest';

import { TooltipProvider } from '@filigran/design-system';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';

import CustomTooltip from '../../components/CustomTooltip';

const shortText = 'Short';
const fullText = 'Full label';

describe('CustomTooltip', () => {
  afterEach(cleanup);

  it('shows the text on hover', async () => {
    render(
      <TooltipProvider delayDuration={0}>
        <CustomTooltip title={fullText}>
          <span>{shortText}</span>
        </CustomTooltip>
      </TooltipProvider>,
    );
    fireEvent.pointerMove(screen.getByText(shortText));
    expect(await screen.findByRole('tooltip')).toHaveTextContent(fullText);
  });

  it('renders the child alone when there is no text', () => {
    render(
      <TooltipProvider delayDuration={0}>
        <CustomTooltip title="">
          <span>{shortText}</span>
        </CustomTooltip>
      </TooltipProvider>,
    );
    fireEvent.pointerMove(screen.getByText(shortText));
    expect(screen.queryByRole('tooltip')).toBeNull();
  });
});

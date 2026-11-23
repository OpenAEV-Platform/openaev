import '@testing-library/jest-dom/vitest';

import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import BlockSide from '../../../../../admin/components/threat_arsenal/customization/BlockSide';
import { type PayloadFieldUpdateOutput } from '../../../../../utils/api-types';

vi.mock('../../../../../components/i18n', () => ({ useFormatter: () => ({ t: (message: string) => message }) }));

const field = (name: PayloadFieldUpdateOutput['field_name'], filigran: unknown, yours: unknown): PayloadFieldUpdateOutput => ({
  field_name: name,
  field_block: name === 'PLATFORMS' ? 'PLATFORMS' : 'EXECUTION',
  field_filigran: filigran as PayloadFieldUpdateOutput['field_filigran'],
  field_yours: yours as PayloadFieldUpdateOutput['field_yours'],
  field_status: 'CONFLICT',
});

describe('BlockSide', () => {
  afterEach(cleanup);

  it('given_platforms_should_showThemAsChips', () => {
    // Act
    render(<BlockSide fields={[field('PLATFORMS', ['Windows', 'Linux'], ['Windows'])]} side="theirs" />);

    // Assert
    expect(screen.getByText('Windows')).toBeInTheDocument();
    expect(screen.getByText('Linux')).toBeInTheDocument();
    expect(screen.queryByText('Platforms')).not.toBeInTheDocument();
  });

  it('given_severalFields_should_showEachUnderItsLabelOneItemPerLine', () => {
    // Act
    const { container } = render(
      <BlockSide
        fields={[
          field('COMMAND_CONTENT', 'nmap -sV target', 'nmap -A target'),
          field('ARGUMENTS', [], [{
            key: 'user',
            default_value: 'admin',
          }, {
            key: 'port',
            default_value: '22',
          }]),
        ]}
        side="mine"
      />,
    );

    // Assert
    expect(screen.getByText('Command')).toBeInTheDocument();
    expect(screen.getByText('Arguments')).toBeInTheDocument();
    expect(container).toHaveTextContent('user = admin port = 22', { normalizeWhitespace: true });
    expect(container.querySelector('mark')).toHaveTextContent('-A');
  });

  it('given_noValue_should_sayItIsEmpty', () => {
    // Act
    render(<BlockSide fields={[field('CLEANUP_COMMAND', null, 'del out.txt')]} side="theirs" />);

    // Assert
    expect(screen.getByText('Empty')).toBeInTheDocument();
  });
});

import '@testing-library/jest-dom/vitest';

import { cleanup, render, screen } from '@testing-library/react';
import { StrictMode } from 'react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { fetchComcheckStatus } from '../../../../actions/Comcheck';
import Comcheck from '../../../../public/components/comcheck/Comcheck';

vi.mock('../../../../actions/Comcheck', () => ({ fetchComcheckStatus: vi.fn() }));
vi.mock('../../../../components/i18n', () => ({
  useFormatter: () => ({
    t: (value: string) => value,
    fldt: (value: string) => value,
  }),
}));

const REFUSAL = 'This communication check has already been answered or is closed.';

const page = (
  <MemoryRouter initialEntries={['/comcheck/status-1']}>
    <Routes>
      <Route path="comcheck/:statusId" element={<Comcheck />} />
    </Routes>
  </MemoryRouter>
);

const renderPage = () => render(page);
const renderPageInStrictMode = () => render(<StrictMode>{page}</StrictMode>);

describe('Comcheck public page', () => {
  afterEach(() => {
    cleanup();
    vi.resetAllMocks();
  });

  it('given a first answer, should show it as successful', async () => {
    // Arrange
    vi.mocked(fetchComcheckStatus).mockResolvedValue({ comcheckstatus_receive_date: '2026-11-23T10:00:00Z' });
    // Act
    renderPage();
    // Assert
    expect(await screen.findByText('successful')).toBeInTheDocument();
    expect(screen.queryByText(REFUSAL)).not.toBeInTheDocument();
  });

  it('given a link opened again (409), should show the plain message and no verdict', async () => {
    // Arrange
    vi.mocked(fetchComcheckStatus).mockRejectedValue({ response: { status: 409 } });
    // Act
    renderPage();
    // Assert
    expect(await screen.findByText(REFUSAL)).toBeInTheDocument();
    expect(screen.queryByText('failed')).not.toBeInTheDocument();
    expect(screen.queryByText('successful')).not.toBeInTheDocument();
  });

  it('given any other error, should keep showing the failed verdict', async () => {
    // Arrange
    vi.mocked(fetchComcheckStatus).mockRejectedValue({ response: { status: 500 } });
    // Act
    renderPage();
    // Assert
    expect(await screen.findByText('failed')).toBeInTheDocument();
    expect(screen.queryByText(REFUSAL)).not.toBeInTheDocument();
  });

  it('given StrictMode replaying the mount effect, should send one request and show the first answer as successful', async () => {
    // Arrange: a second request would be refused (409), as the server does once the first one is recorded
    vi.mocked(fetchComcheckStatus)
      .mockResolvedValueOnce({ comcheckstatus_receive_date: '2026-11-23T10:00:00Z' })
      .mockRejectedValue({ response: { status: 409 } });
    // Act
    renderPageInStrictMode();
    // Assert
    expect(await screen.findByText('successful')).toBeInTheDocument();
    expect(screen.queryByText(REFUSAL)).not.toBeInTheDocument();
    expect(fetchComcheckStatus).toHaveBeenCalledTimes(1);
  });

  it('given the page opened again later, should send a new request', async () => {
    // Arrange
    vi.mocked(fetchComcheckStatus)
      .mockResolvedValueOnce({ comcheckstatus_receive_date: '2026-11-23T10:00:00Z' })
      .mockRejectedValue({ response: { status: 409 } });
    renderPage();
    expect(await screen.findByText('successful')).toBeInTheDocument();
    cleanup();
    // Act
    renderPage();
    // Assert
    expect(await screen.findByText(REFUSAL)).toBeInTheDocument();
    expect(fetchComcheckStatus).toHaveBeenCalledTimes(2);
  });
});

import React from 'react';
import { act, fireEvent, render, screen } from '@testing-library/react';

import HoldToConfirm from '../components/HoldToConfirm';

function renderHold(props = {}) {
  const onConfirm = props.onConfirm ?? jest.fn().mockResolvedValue();
  render(
    <HoldToConfirm
      label="归档"
      successLabel="已归档"
      errorLabel="归档失败"
      onConfirm={onConfirm}
      {...props}
    />
  );
  return { onConfirm, button: screen.getByTestId('hold-to-confirm') };
}

async function fillRing(button) {
  fireEvent.pointerDown(button, { pointerType: 'mouse', button: 0 });
  act(() => {
    jest.advanceTimersByTime(1600);
  });
  await act(async () => {
    await Promise.resolve();
  });
  act(() => {
    jest.advanceTimersByTime(400);
  });
  await act(async () => {
    await Promise.resolve();
  });
}

beforeEach(() => {
  jest.useFakeTimers();
});

afterEach(() => {
  jest.useRealTimers();
});

test('fills for 1.5s, shakes once and shows the check', async () => {
  const { onConfirm, button } = renderHold();

  await fillRing(button);

  expect(onConfirm).toHaveBeenCalledTimes(1);
  expect(button).toHaveClass('is-success');
  expect(screen.getByText('✓ 已归档')).toBeInTheDocument();
});

test('releasing early cancels and rewinds to zero', () => {
  const { onConfirm, button } = renderHold();

  fireEvent.pointerDown(button, { pointerType: 'mouse', button: 0 });
  act(() => {
    jest.advanceTimersByTime(500);
  });
  fireEvent.pointerUp(button, { pointerType: 'mouse', button: 0 });
  act(() => {
    jest.advanceTimersByTime(300);
  });

  expect(onConfirm).not.toHaveBeenCalled();
  expect(button).toHaveClass('is-idle');
  expect(button).not.toHaveClass('is-success');
  expect(screen.getByText('归档')).toBeInTheDocument();
});

test('shows the error label when the write fails', async () => {
  const onConfirm = jest.fn().mockRejectedValue(new Error('boom'));
  const { button } = renderHold({ onConfirm });

  await fillRing(button);

  expect(button).toHaveClass('is-error');
  expect(screen.getByText('归档失败')).toBeInTheDocument();
});

import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import SearchableSelect from '../components/SearchableSelect';

const mockOptions = [
  { id: 1, name: 'Option A' },
  { id: 2, name: 'Option B' },
  { id: 3, name: 'Another Option' },
];

describe('SearchableSelect', () => {
  test('renders with label and options', () => {
    const onChange = jest.fn();
    render(
      <SearchableSelect
        label="Test Label"
        options={mockOptions}
        onChange={onChange}
      />
    );

    expect(screen.getByText('Test Label')).toBeInTheDocument();

    const input = screen.getByPlaceholderText('Search and select...');
    fireEvent.focus(input);

    expect(screen.getByText('Option A')).toBeInTheDocument();
    expect(screen.getByText('Option B')).toBeInTheDocument();
    expect(screen.getByText('Another Option')).toBeInTheDocument();
  });

  test('typing filters options', () => {
    const onChange = jest.fn();
    render(
      <SearchableSelect
        label="Test"
        options={mockOptions}
        onChange={onChange}
      />
    );

    const input = screen.getByPlaceholderText('Search and select...');
    fireEvent.focus(input);
    fireEvent.change(input, { target: { value: 'B' } });

    expect(screen.queryByText('Option A')).not.toBeInTheDocument();
    expect(screen.getByText('Option B')).toBeInTheDocument();
    expect(screen.queryByText('Another Option')).not.toBeInTheDocument();
  });

  test('clicking option triggers onChange', () => {
    const onChange = jest.fn();
    render(
      <SearchableSelect
        label="Test"
        options={mockOptions}
        onChange={onChange}
      />
    );

    const input = screen.getByPlaceholderText('Search and select...');
    fireEvent.focus(input);

    fireEvent.click(screen.getByText('Option A'));

    expect(onChange).toHaveBeenCalledTimes(1);
    expect(onChange).toHaveBeenCalledWith(1);
  });

  test('addNewLink renders a link when provided', () => {
    render(
      <SearchableSelect
        label="Test"
        options={mockOptions}
        onChange={() => {}}
        addNewLink="https://example.com/new"
      />
    );

    const link = screen.getByText('+ New');
    expect(link.tagName).toBe('A');
    expect(link).toHaveAttribute('href', 'https://example.com/new');
    expect(link).toHaveAttribute('target', '_blank');
    expect(link).toHaveAttribute('rel', 'noopener noreferrer');
  });

  test('onAddNew renders a button when provided', () => {
    const onAddNew = jest.fn();
    render(
      <SearchableSelect
        label="Test"
        options={mockOptions}
        onChange={() => {}}
        onAddNew={onAddNew}
      />
    );

    const button = screen.getByText('+ New');
    expect(button.tagName).toBe('BUTTON');
    expect(button).toHaveAttribute('type', 'button');

    fireEvent.click(button);
    expect(onAddNew).toHaveBeenCalledTimes(1);
  });

  test('empty options shows placeholder text', () => {
    render(
      <SearchableSelect
        label="Test"
        options={[]}
        onChange={() => {}}
      />
    );

    const input = screen.getByPlaceholderText('Search and select...');
    expect(input).toBeInTheDocument();
    expect(input).toHaveAttribute('placeholder', 'Search and select...');

    // Focus the input — no dropdown renders because filtered options are empty
    fireEvent.focus(input);
    expect(screen.queryByText('Option A')).not.toBeInTheDocument();
  });
});

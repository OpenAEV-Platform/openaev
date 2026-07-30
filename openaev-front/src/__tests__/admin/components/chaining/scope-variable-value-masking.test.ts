import { describe, expect, it } from 'vitest';

import maskScopeVariableValue from '../../../../admin/components/chaining/scope-variable-value-masking';

describe('maskScopeVariableValue', () => {
  it('masks password values with first and last characters visible', () => {
    // Arrange
    const value = 'F123453';

    // Act
    const maskedValue = maskScopeVariableValue('password', value);

    // Assert
    expect(maskedValue).toBe('F*****3');
  });

  it('masks hash values with first and last three characters visible', () => {
    // Arrange
    const value = '534abcdEFGH36';

    // Act
    const maskedValue = maskScopeVariableValue('hash', value);

    // Assert
    expect(maskedValue).toBe('534*******H36');
  });

  it('fully masks short sensitive values', () => {
    // Arrange
    const passwordValue = 'ab';
    const hashValue = '12345';

    // Act
    const maskedPassword = maskScopeVariableValue('password', passwordValue);
    const maskedHash = maskScopeVariableValue('hash', hashValue);

    // Assert
    expect(maskedPassword).toBe('**');
    expect(maskedHash).toBe('*****');
  });

  it('keeps non-sensitive variable values unchanged', () => {
    // Arrange
    const value = 'jane.doe';

    // Act
    const maskedValue = maskScopeVariableValue('username', value);

    // Assert
    expect(maskedValue).toBe(value);
  });
});

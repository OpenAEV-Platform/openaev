package io.openaev.database.model;

import java.util.EnumMap;
import java.util.Map;

public record ChainingOutputType(
    ContractOutputType contractOutputType,
    ChainingTypeKind kind,
    PrimitiveType primitiveType,
    ComplexType complexType) {

  private static final Map<ContractOutputType, ChainingOutputType> INDEX =
      new EnumMap<>(ContractOutputType.class);

  static {
    registerPrimitive(ContractOutputType.Text, PrimitiveType.Text);
    registerPrimitive(ContractOutputType.Number, PrimitiveType.Number);
    registerPrimitive(ContractOutputType.Port, PrimitiveType.Port);
    registerPrimitive(ContractOutputType.IPv4, PrimitiveType.IPv4);
    registerPrimitive(ContractOutputType.IPv6, PrimitiveType.IPv6);

    registerComplex(ContractOutputType.Credentials, ComplexType.Credentials);
    registerComplex(ContractOutputType.PortsScan, ComplexType.PortsScan);
    registerComplex(ContractOutputType.Share, ComplexType.Share);
    registerComplex(ContractOutputType.Vulnerability, ComplexType.Vulnerability);
    registerComplex(ContractOutputType.AsreproastableAccount, ComplexType.AsreproastableAccount);
    registerComplex(ContractOutputType.KerberoastableAccount, ComplexType.KerberoastableAccount);
    registerComplex(ContractOutputType.CVE, ComplexType.CVE);
    registerComplex(ContractOutputType.Username, ComplexType.Username);
    registerComplex(ContractOutputType.AdminUsername, ComplexType.AdminUsername);
    registerComplex(ContractOutputType.Group, ComplexType.Group);
    registerComplex(ContractOutputType.Computer, ComplexType.Computer);
    registerComplex(ContractOutputType.PasswordPolicy, ComplexType.PasswordPolicy);
    registerComplex(ContractOutputType.Delegation, ComplexType.Delegation);
    registerComplex(ContractOutputType.Sid, ComplexType.Sid);
    registerComplex(
        ContractOutputType.AccountWithPasswordNotRequired,
        ComplexType.AccountWithPasswordNotRequired);
    registerComplex(ContractOutputType.Asset, ComplexType.Asset);

    registerNonChainable(ContractOutputType.ExpectationSignature);
  }

  private static void registerPrimitive(
      ContractOutputType outputType, PrimitiveType primitiveType) {
    INDEX.put(
        outputType,
        new ChainingOutputType(outputType, ChainingTypeKind.PRIMITIVE, primitiveType, null));
  }

  private static void registerComplex(ContractOutputType outputType, ComplexType complexType) {
    INDEX.put(
        outputType,
        new ChainingOutputType(outputType, ChainingTypeKind.COMPLEX, null, complexType));
  }

  private static void registerNonChainable(ContractOutputType outputType) {
    INDEX.put(
        outputType, new ChainingOutputType(outputType, ChainingTypeKind.NOT_CHAINABLE, null, null));
  }

  public static ChainingOutputType fromContractOutputType(ContractOutputType type) {
    ChainingOutputType outputType = INDEX.get(type);
    if (outputType == null) {
      throw new IllegalArgumentException("No chaining output classification found for " + type);
    }
    return outputType;
  }
}

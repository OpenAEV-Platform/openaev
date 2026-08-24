package io.openaev.injector_contract.variables.contract;

import io.openaev.database.model.Variable;
import io.openaev.injector_contract.ContractCardinality;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface VariableContract {
  String name();

  String description() default "";

  Variable.VariableType type() default Variable.VariableType.String;

  ContractCardinality cardinality() default ContractCardinality.One;
}

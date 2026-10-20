package io.github.temporalrift.game.shared.domain.port.out;

import java.util.Map;

/** A module's check that an execution context's rules hold every rule the module uses, each in range. */
public interface ExecutionRulesValidator {

    /**
     * Fails with an {@code InvalidExecutionRulesException} naming the first rule that is missing or invalid.
     *
     * @param properties the rules as flat property keys to text values
     */
    void validate(Map<String, String> properties);
}

package org.eclipse.kuksa.testapp.domain

import org.eclipse.kuksa.testapp.model.ConnectionConfig

sealed class ValidationResult {
    data object Ok : ValidationResult()
    data class Error(val message: String) : ValidationResult()
}

object ConfigValidator {
    private const val MIN_PORT = 1
    private const val MAX_PORT = 65535

    fun validate(config: ConnectionConfig): ValidationResult {
        if (config.host.isBlank()) {
            return ValidationResult.Error("Host cannot be blank")
        }
        if (config.port < MIN_PORT || config.port > MAX_PORT) {
            return ValidationResult.Error("Port must be between $MIN_PORT and $MAX_PORT")
        }
        return ValidationResult.Ok
    }
}

package ua.lab3.registry;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record Thresholds(
        @NotNull @DecimalMin("0") @DecimalMax("1") Double warning,
        @NotNull @DecimalMin("0") @DecimalMax("1") Double anomaly) {

    @AssertTrue(message = "warning must be less than anomaly")
    boolean isOrdered() {
        return warning == null || anomaly == null || warning < anomaly;
    }
}

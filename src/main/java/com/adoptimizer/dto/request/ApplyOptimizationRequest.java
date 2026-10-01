package com.adoptimizer.dto.request;

import com.adoptimizer.model.OptimizationType;
import com.adoptimizer.validation.EnumCode;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ApplyOptimizationRequest {

    @NotBlank(message = "Choose an optimization")
    @EnumCode(enumClass = OptimizationType.class, message = "Unknown optimization type")
    private String type;
}

package com.adoptimizer.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record MoneyTrendResponse(List<String> labels, List<BigDecimal> revenue, List<BigDecimal> adSpend) {
}

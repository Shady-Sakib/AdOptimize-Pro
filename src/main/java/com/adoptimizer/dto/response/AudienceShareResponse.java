package com.adoptimizer.dto.response;

public record AudienceShareResponse(String audience, String label, long impressions, long campaigns, double percent) {
}

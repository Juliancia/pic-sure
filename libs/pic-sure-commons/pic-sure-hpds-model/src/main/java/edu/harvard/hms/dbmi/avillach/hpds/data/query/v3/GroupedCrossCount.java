package edu.harvard.hms.dbmi.avillach.hpds.data.query.v3;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * Result of a {@code GROUPED_CROSS_COUNT} query: the distribution of {@code target} within each category of {@code groupBy}, restricted to
 * the query cohort. For a categorical target the inner keys are categories; for a continuous target they are observed values. Only groups
 * and values with at least one patient are included.
 */
public record GroupedCrossCount(
    @Schema(description = "Categorical concept path the cohort is split by") String groupBy,
    @Schema(description = "Concept path whose distribution is counted within each group") String target,
    @Schema(description = "True when the target is continuous and inner keys are numeric values") boolean targetContinuous,
    @Schema(
        description = "Patient counts keyed by group category, then by target category or value"
    ) Map<String, Map<String, Integer>> counts
) {
}

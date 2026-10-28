package edu.harvard.dbmi.avillach.visualization.model;

import edu.harvard.hms.dbmi.avillach.hpds.data.query.v3.Query;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * The {@code POST /{backend}/distributions/grouped} request body: the cohort query, the categorical concept to split the cohort by, and the
 * concept whose distribution is shown within each group.
 */
public record GroupedDistributionRequest(
    @NotNull(message = "Request must contain a 'query' field") Query query,
    @NotBlank(message = "Request must contain a 'groupBy' concept path") String groupBy,
    @NotBlank(message = "Request must contain a 'target' concept path") String target
) {
}

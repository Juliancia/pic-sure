package edu.harvard.dbmi.avillach.visualization.service;

import edu.harvard.dbmi.avillach.visualization.error.BadVisualizationRequestException;
import edu.harvard.dbmi.avillach.visualization.error.ConsentDeniedException;
import edu.harvard.dbmi.avillach.visualization.error.HpdsUpstreamException;
import edu.harvard.dbmi.avillach.visualization.model.AccessType;
import edu.harvard.dbmi.avillach.visualization.model.GroupedDistributionData;
import edu.harvard.dbmi.avillach.visualization.model.GroupedDistributionRequest;
import edu.harvard.dbmi.avillach.visualization.processing.GroupedDistributionProcessor;
import edu.harvard.hms.dbmi.avillach.hpds.data.query.ResultType;
import edu.harvard.hms.dbmi.avillach.hpds.data.query.v3.GroupedCrossCount;
import edu.harvard.hms.dbmi.avillach.hpds.data.query.v3.Query;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;

/**
 * Compares two variables in one HPDS {@code GROUPED_CROSS_COUNT} query. Authorized access only: query-service does not obfuscate grouped
 * counts, and its open path rejects the result type.
 */
@Service
public class GroupedDistributionService {

    private static final Logger logger = LoggerFactory.getLogger(GroupedDistributionService.class);

    private final QueryServiceClient queryServiceClient;
    private final GroupedDistributionProcessor processor;

    public GroupedDistributionService(QueryServiceClient queryServiceClient, GroupedDistributionProcessor processor) {
        this.queryServiceClient = queryServiceClient;
        this.processor = processor;
    }

    public GroupedDistributionData generate(
        GroupedDistributionRequest request, AccessType accessType, QueryServiceClient.GatewayIdentity identity, String requestId
    ) {
        if (accessType != AccessType.AUTHORIZED) {
            throw new BadVisualizationRequestException("Comparing two variables is only available to logged-in users");
        }
        if (request.groupBy().equals(request.target())) {
            throw new BadVisualizationRequestException("Choose two different variables to compare");
        }
        Query query = request.query();
        Query grouped = new Query(
            List.of(request.groupBy(), request.target()), query.authorizationFilters(), query.phenotypicClause(), query.genomicFilters(),
            ResultType.GROUPED_CROSS_COUNT, query.picsureId(), null
        );
        try {
            GroupedCrossCount crossCount = queryServiceClient.getAuthGroupedCrossCount(grouped, identity, requestId);
            return processor.process(crossCount);
        } catch (HttpStatusCodeException e) {
            int status = e.getStatusCode().value();
            logger.error("Query service returned HTTP {} for grouped cross count requestId={}", status, requestId, e);
            if (status == 400) {
                throw new BadVisualizationRequestException("These variables cannot be compared: " + e.getResponseBodyAsString(), e);
            }
            if (status == 403) {
                throw new ConsentDeniedException("Consent does not permit this visualization query", e);
            }
            throw new HpdsUpstreamException("Query service request failed with status " + status + ": " + e.getStatusText(), e);
        } catch (ResourceAccessException e) {
            logger.error("Could not reach query service for grouped cross count requestId={}", requestId, e);
            throw new HpdsUpstreamException("Query service request failed: " + e.getMessage(), e);
        }
    }
}

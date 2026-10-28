package edu.harvard.dbmi.avillach.visualization.controller;

import edu.harvard.dbmi.avillach.visualization.logging.AuditLoggingContext;
import edu.harvard.dbmi.avillach.visualization.model.GroupedDistributionData;
import edu.harvard.dbmi.avillach.visualization.model.GroupedDistributionRequest;
import edu.harvard.dbmi.avillach.visualization.service.AccessTypeResolver;
import edu.harvard.dbmi.avillach.visualization.service.GroupedDistributionService;
import edu.harvard.dbmi.avillach.visualization.service.QueryServiceClient;
import edu.harvard.hms.dbmi.avillach.commons.identity.GatewayUserResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GroupedDistributionController {

    private final GroupedDistributionService groupedDistributionService;
    private final AccessTypeResolver accessTypeResolver;

    public GroupedDistributionController(GroupedDistributionService groupedDistributionService, AccessTypeResolver accessTypeResolver) {
        this.groupedDistributionService = groupedDistributionService;
        this.accessTypeResolver = accessTypeResolver;
    }

    @PostMapping("/{backend}/distributions/grouped")
    public ResponseEntity<GroupedDistributionData> groupedDistribution(
        @PathVariable String backend, @Valid @RequestBody GroupedDistributionRequest request, HttpServletRequest servletRequest
    ) {
        GroupedDistributionData data = groupedDistributionService.generate(
            request, accessTypeResolver.resolve(backend), gatewayIdentity(servletRequest), AuditLoggingContext.requestId(servletRequest)
        );
        return ResponseEntity.ok(data);
    }

    /** Same identity forwarding as {@link DistributionController}; the gateway strips client-supplied values of these headers. */
    private static QueryServiceClient.GatewayIdentity gatewayIdentity(HttpServletRequest request) {
        return new QueryServiceClient.GatewayIdentity(
            request.getHeader(GatewayUserResolver.HEADER_USER_ID), request.getHeader(GatewayUserResolver.HEADER_USER_SUBJECT),
            request.getHeader(GatewayUserResolver.HEADER_USER_EMAIL), request.getHeader(GatewayUserResolver.HEADER_USER_ROLES),
            request.getHeader(GatewayUserResolver.HEADER_USER_PRIVILEGES), request.getHeader("Authorization")
        );
    }
}

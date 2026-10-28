package edu.harvard.dbmi.avillach.visualization.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import edu.harvard.dbmi.avillach.visualization.error.BadVisualizationRequestException;
import edu.harvard.dbmi.avillach.visualization.model.AccessType;
import edu.harvard.dbmi.avillach.visualization.model.GroupedDistributionData;
import edu.harvard.dbmi.avillach.visualization.model.GroupedDistributionRequest;
import edu.harvard.dbmi.avillach.visualization.processing.BinningService;
import edu.harvard.dbmi.avillach.visualization.processing.GroupedDistributionProcessor;
import edu.harvard.hms.dbmi.avillach.hpds.data.query.ResultType;
import edu.harvard.hms.dbmi.avillach.hpds.data.query.v3.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

@ExtendWith(MockitoExtension.class)
class GroupedDistributionServiceTest {

    private static final QueryServiceClient.GatewayIdentity IDENTITY =
        new QueryServiceClient.GatewayIdentity("u-1", "sub-1", "a@b", "ROLE_X", "PRIV_A");
    private static final String SEX = "\\demographics\\SEX\\";
    private static final String DIAGNOSIS = "\\clinical\\DIAGNOSIS\\";

    @Mock
    private QueryServiceClient queryServiceClient;

    private GroupedDistributionService service;

    @BeforeEach
    void setUp() {
        service = new GroupedDistributionService(queryServiceClient, new GroupedDistributionProcessor(new BinningService(), 7, 10));
    }

    private static GroupedDistributionRequest request(String groupBy, String target) {
        PhenotypicFilter filter = new PhenotypicFilter(PhenotypicFilterType.FILTER, SEX, Set.of("female", "male"), null, null, null);
        Query query = new Query(List.of(), List.of(), filter, List.of(), ResultType.COUNT, null, null);
        return new GroupedDistributionRequest(query, groupBy, target);
    }

    @Test
    void authorized_sendsOneGroupedQueryKeepingTheCohort() {
        when(queryServiceClient.getAuthGroupedCrossCount(any(), any(), any())).thenReturn(
            new GroupedCrossCount(SEX, DIAGNOSIS, false, Map.of("female", Map.of("A", 3), "male", Map.of("A", 1, "B", 2)))
        );
        GroupedDistributionRequest req = request(SEX, DIAGNOSIS);

        GroupedDistributionData data = service.generate(req, AccessType.AUTHORIZED, IDENTITY, "req-1");

        ArgumentCaptor<Query> sent = ArgumentCaptor.forClass(Query.class);
        verify(queryServiceClient, times(1)).getAuthGroupedCrossCount(sent.capture(), eq(IDENTITY), eq("req-1"));
        assertEquals(List.of(SEX, DIAGNOSIS), sent.getValue().select());
        assertEquals(ResultType.GROUPED_CROSS_COUNT, sent.getValue().expectedResultType());
        assertEquals(req.query().phenotypicClause(), sent.getValue().phenotypicClause());
        assertEquals(Map.of("A", 3, "B", 0), data.groups().get("female"));
    }

    @Test
    void openAccess_isRejectedWithoutQuerying() {
        assertThrows(
            BadVisualizationRequestException.class, () -> service.generate(request(SEX, DIAGNOSIS), AccessType.OPEN, IDENTITY, "req-1")
        );
        verifyNoInteractions(queryServiceClient);
    }

    @Test
    void sameVariableTwice_isRejected() {
        assertThrows(
            BadVisualizationRequestException.class, () -> service.generate(request(SEX, SEX), AccessType.AUTHORIZED, IDENTITY, "req-1")
        );
    }

    @Test
    void hpdsBadRequest_becomesBadVisualizationRequest() {
        when(queryServiceClient.getAuthGroupedCrossCount(any(), any(), any())).thenThrow(
            HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST, "Bad Request", HttpHeaders.EMPTY,
                "GROUPED_CROSS_COUNT group concept must be categorical".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8
            )
        );

        BadVisualizationRequestException e = assertThrows(
            BadVisualizationRequestException.class,
            () -> service.generate(request(SEX, DIAGNOSIS), AccessType.AUTHORIZED, IDENTITY, "req-1")
        );
        assertTrue(e.getMessage().contains("must be categorical"));
    }
}

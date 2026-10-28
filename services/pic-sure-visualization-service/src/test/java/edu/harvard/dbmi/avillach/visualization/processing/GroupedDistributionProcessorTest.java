package edu.harvard.dbmi.avillach.visualization.processing;

import static org.junit.jupiter.api.Assertions.*;

import edu.harvard.dbmi.avillach.visualization.error.BadVisualizationRequestException;
import edu.harvard.dbmi.avillach.visualization.model.GroupedDistributionData;
import edu.harvard.hms.dbmi.avillach.hpds.data.query.v3.GroupedCrossCount;
import java.util.*;
import org.junit.jupiter.api.Test;

class GroupedDistributionProcessorTest {

    private static final String SEX = "\\demographics\\SEX\\";
    private static final String DIAGNOSIS = "\\clinical\\DIAGNOSIS\\";
    private static final String AGE = "\\demographics\\AGE\\";

    private final GroupedDistributionProcessor processor = new GroupedDistributionProcessor(new BinningService(), 2, 3);

    @Test
    void categoricalTarget_everyGroupGetsSameCategoriesWithZerosAndOther() {
        Map<String, Map<String, Integer>> counts = new LinkedHashMap<>();
        counts.put("female", Map.of("A", 5, "B", 1, "C", 1));
        counts.put("male", Map.of("A", 2, "D", 4));

        GroupedDistributionData data = processor.process(new GroupedCrossCount(SEX, DIAGNOSIS, false, counts));

        // Totals: A=7, D=4, B=1, C=1 -> keep the top 2 (A, D) and fold B and C into Other
        assertEquals(List.of("A", "D", "Other"), data.categories());
        assertEquals(Map.of("A", 5, "D", 0, "Other", 2), data.groups().get("female"));
        assertEquals(Map.of("A", 2, "D", 4, "Other", 0), data.groups().get("male"));
        assertEquals("DIAGNOSIS", data.xaxisName());
        assertEquals("SEX", data.groupName());
        assertFalse(data.continuous());
    }

    @Test
    void continuousTarget_groupsShareBins() {
        Map<String, Map<String, Integer>> counts = new LinkedHashMap<>();
        counts.put("female", Map.of("20.0", 3, "35.0", 1));
        counts.put("male", Map.of("60.0", 2));

        GroupedDistributionData data = processor.process(new GroupedCrossCount(SEX, AGE, true, counts));

        assertTrue(data.continuous());
        assertEquals(data.categories(), List.copyOf(data.groups().get("female").keySet()));
        assertEquals(data.categories(), List.copyOf(data.groups().get("male").keySet()));
        assertEquals(4, data.groups().get("female").values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(2, data.groups().get("male").values().stream().mapToInt(Integer::intValue).sum());
    }

    @Test
    void tooManyGroups_isRejected() {
        Map<String, Map<String, Integer>> counts = new LinkedHashMap<>();
        for (String group : List.of("a", "b", "c", "d")) {
            counts.put(group, Map.of("A", 1));
        }

        assertThrows(BadVisualizationRequestException.class, () -> processor.process(new GroupedCrossCount(SEX, DIAGNOSIS, false, counts)));
    }
}

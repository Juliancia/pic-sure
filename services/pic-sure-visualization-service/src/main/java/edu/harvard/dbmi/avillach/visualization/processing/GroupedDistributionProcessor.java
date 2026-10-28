package edu.harvard.dbmi.avillach.visualization.processing;

import edu.harvard.dbmi.avillach.visualization.error.BadVisualizationRequestException;
import edu.harvard.dbmi.avillach.visualization.model.GroupedDistributionData;
import edu.harvard.hms.dbmi.avillach.hpds.data.query.v3.GroupedCrossCount;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Shapes an HPDS {@link GroupedCrossCount} into one grouped chart. A continuous target is binned once across all groups so bins line up; a
 * categorical target keeps the categories with the largest totals across groups and folds the rest into "Other", so every group shares the
 * same categories.
 */
@Component
public class GroupedDistributionProcessor {

    private static final String OTHER = "Other";

    private final BinningService binningService;
    private final int maxCategories;
    private final int maxGroups;

    public GroupedDistributionProcessor(
        BinningService binningService, @Value("${chart.categorical.max-categories:7}") int maxCategories,
        @Value("${chart.grouped.max-groups:10}") int maxGroups
    ) {
        this.binningService = binningService;
        this.maxCategories = maxCategories;
        this.maxGroups = maxGroups;
    }

    public GroupedDistributionData process(GroupedCrossCount crossCount) {
        Map<String, Map<String, Integer>> counts = crossCount.counts() != null ? crossCount.counts() : Map.of();
        if (counts.size() > maxGroups) {
            throw new BadVisualizationRequestException(
                "Too many groups to compare (" + counts.size() + "); choose a variable with at most " + maxGroups + " categories"
            );
        }

        Map<String, Map<String, Integer>> groups =
            crossCount.targetContinuous() ? binningService.bucketDataShared(counts) : topCategoriesAcrossGroups(counts);
        List<String> categories = groups.values().stream().findFirst().map(g -> List.copyOf(g.keySet())).orElse(List.of());

        return new GroupedDistributionData(
            crossCount.groupBy(), crossCount.target(),
            DistributionMetadata.titleFor(crossCount.target()) + " by " + DistributionMetadata.xAxisLabelFor(crossCount.groupBy()),
            DistributionMetadata.xAxisLabelFor(crossCount.groupBy()), DistributionMetadata.xAxisLabelFor(crossCount.target()),
            "Number of Participants", crossCount.targetContinuous(), categories, groups
        );
    }

    private Map<String, Map<String, Integer>> topCategoriesAcrossGroups(Map<String, Map<String, Integer>> counts) {
        Map<String, Integer> totals = new HashMap<>();
        counts.values().forEach(values -> values.forEach((category, count) -> totals.merge(category, count, Integer::sum)));
        List<String> ordered = totals.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
            .map(Map.Entry::getKey).toList();
        boolean needsOther = ordered.size() > maxCategories;
        List<String> kept = needsOther ? ordered.subList(0, maxCategories) : ordered;

        Map<String, Map<String, Integer>> out = new LinkedHashMap<>();
        counts.forEach((group, values) -> {
            Map<String, Integer> row = new LinkedHashMap<>();
            kept.forEach(category -> row.put(category, values.getOrDefault(category, 0)));
            if (needsOther) {
                int other = values.entrySet().stream().filter(e -> !kept.contains(e.getKey())).mapToInt(Map.Entry::getValue).sum();
                row.merge(OTHER, other, Integer::sum);
            }
            out.put(group, row);
        });
        return out;
    }
}

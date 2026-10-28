package edu.harvard.dbmi.avillach.visualization.model;

import java.util.List;
import java.util.Map;

/**
 * One grouped chart: for each group (category of {@code groupBy}) the patient count in every {@code categories} entry of the target. Every
 * group carries every category, with 0 where it has no patients, so the frontend can draw the series side by side.
 */
public record GroupedDistributionData(
    String groupBy, String target, String title, String groupName, String xaxisName, String yaxisName, boolean continuous,
    List<String> categories, Map<String, Map<String, Integer>> groups
) {
}

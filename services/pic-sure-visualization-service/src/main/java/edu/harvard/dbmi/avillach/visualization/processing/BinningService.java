package edu.harvard.dbmi.avillach.visualization.processing;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class BinningService {

    private static final double THIRD = 1.0 / 3.0;

    public Map<String, Map<String, Integer>> binContinuousData(Map<String, Map<String, Integer>> continuousDataMap) {
        Map<String, Map<String, Integer>> result = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Integer>> entry : continuousDataMap.entrySet()) {
            result.put(entry.getKey(), bucketData(entry.getValue()));
        }
        return result;
    }

    public Map<String, Integer> bucketData(Map<String, Integer> originalMap) {
        if (originalMap == null || originalMap.isEmpty()) {
            return new LinkedHashMap<>();
        }

        Map<Double, Integer> data = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : originalMap.entrySet()) {
            try {
                data.put(Double.parseDouble(entry.getKey()), entry.getValue());
            } catch (NumberFormatException e) {
                // skip non-numeric keys
            }
        }

        if (data.isEmpty()) {
            return new LinkedHashMap<>();
        }

        boolean isSameMinMax = data.size() == 1;

        int numBins = calcNumBins(data);
        double min = data.keySet().stream().min(Double::compareTo).orElse(0.0);
        double max = data.keySet().stream().max(Double::compareTo).orElse(0.0);

        if (numBins <= 0) {
            numBins = 1;
        }

        double binSize = (max - min) / numBins;
        if (binSize <= 0.0) {
            binSize = 1.0;
        }

        Map<Integer, Integer> counts = createBinsAndMergeCounts(data, numBins, min, binSize);

        int bucketMax = counts.keySet().stream().max(Integer::compareTo).orElse(0);
        Map<Integer, Integer> results = new LinkedHashMap<>();
        Map<Integer, List<Double>> ranges = new HashMap<>();
        for (int key = 0; key <= bucketMax; key++) {
            double rangeStart = min + (key * binSize);
            double rangeEnd = min + ((key + 1) * binSize);
            ranges.put(key, new ArrayList<>(List.of(rangeStart, rangeEnd)));
            results.put(key, counts.getOrDefault(key, 0));
        }

        return createLabelsForBins(results, ranges, isSameMinMax);
    }

    /**
     * Bins several value distributions with one shared set of bins, computed from their combined values, so the same label means the same
     * range in every group. Each result has every bin label, in ascending order, with 0 for empty bins.
     */
    public Map<String, Map<String, Integer>> bucketDataShared(Map<String, Map<String, Integer>> groups) {
        Map<String, Map<Double, Integer>> parsed = new LinkedHashMap<>();
        Map<Double, Integer> combined = new LinkedHashMap<>();
        groups.forEach((group, values) -> {
            Map<Double, Integer> data = new LinkedHashMap<>();
            values.forEach((key, count) -> {
                try {
                    data.merge(Double.parseDouble(key), count, Integer::sum);
                } catch (NumberFormatException e) {
                    // skip non-numeric keys
                }
            });
            data.forEach((value, count) -> combined.merge(value, count, Integer::sum));
            parsed.put(group, data);
        });

        Map<String, Map<String, Integer>> out = new LinkedHashMap<>();
        if (combined.isEmpty()) {
            groups.keySet().forEach(group -> out.put(group, new LinkedHashMap<>()));
            return out;
        }

        boolean isSameMinMax = combined.size() == 1;
        int numBins = Math.max(calcNumBins(combined), 1);
        double min = combined.keySet().stream().min(Double::compareTo).orElse(0.0);
        double max = combined.keySet().stream().max(Double::compareTo).orElse(0.0);
        double binSize = (max - min) / numBins;
        if (binSize <= 0.0) {
            binSize = 1.0;
        }
        int bucketMax = createBinsAndMergeCounts(combined, numBins, min, binSize).keySet().stream().max(Integer::compareTo).orElse(0);

        Map<Integer, List<Double>> ranges = new HashMap<>();
        for (int key = 0; key <= bucketMax; key++) {
            ranges.put(key, new ArrayList<>(List.of(min + (key * binSize), min + ((key + 1) * binSize))));
        }
        for (Map.Entry<String, Map<Double, Integer>> group : parsed.entrySet()) {
            Map<Integer, Integer> counts = createBinsAndMergeCounts(group.getValue(), numBins, min, binSize);
            Map<Integer, Integer> results = new LinkedHashMap<>();
            for (int key = 0; key <= bucketMax; key++) {
                results.put(key, counts.getOrDefault(key, 0));
            }
            out.put(group.getKey(), createLabelsForBins(results, ranges, isSameMinMax));
        }
        return out;
    }

    private static int calcNumBins(Map<Double, Integer> countMap) {
        double[] keys = countMap.keySet().stream().mapToDouble(Double::doubleValue).toArray();
        DescriptiveStatistics da = new DescriptiveStatistics(keys);
        double smallestKey = da.getMin();
        double largestKey = da.getMax();
        if (smallestKey == largestKey) return 1;
        double binWidth = (3.5 * da.getStandardDeviation()) / Math.pow(countMap.size(), THIRD);
        return (int) Math.round((largestKey - smallestKey) / binWidth);
    }

    private static Map<Integer, Integer> createBinsAndMergeCounts(Map<Double, Integer> data, int numBins, double min, double binSize) {
        Map<Integer, Integer> results = new LinkedHashMap<>();
        for (Map.Entry<Double, Integer> entry : data.entrySet()) {
            int bin = (int) Math.floor((entry.getKey() - min) / binSize);
            if (bin < numBins) {
                results.merge(bin, entry.getValue(), Integer::sum);
            } else {
                // Value at the exact max — merge into last bin
                results.merge(numBins - 1, entry.getValue(), Integer::sum);
            }
        }
        return results;
    }

    private static Map<String, Integer> createLabelsForBins(
        Map<Integer, Integer> results, Map<Integer, List<Double>> ranges, boolean isSameMinMax
    ) {
        Map<String, Integer> finalMap = new LinkedHashMap<>();
        String label = "";
        for (Map.Entry<Integer, Integer> bucket : results.entrySet()) {
            double minForLabel = ranges.get(bucket.getKey()).stream().min(Double::compareTo).orElse(0.0);
            double maxForLabel = ranges.get(bucket.getKey()).stream().max(Double::compareTo).orElse(0.0);
            if (minForLabel == maxForLabel || isSameMinMax) {
                label = String.format("%.1f", minForLabel);
            } else {
                label = String.format("%.1f", minForLabel) + " - " + String.format("%.1f", maxForLabel);
            }
            // Adjacent bins can round to the same %.1f label; merge so counts aren't dropped
            finalMap.merge(label, bucket.getValue(), Integer::sum);
        }

        Integer lastCount = finalMap.get(label);
        if (lastCount != null && finalMap.size() > 1) {
            String newLabel = label;
            int hasDash = label.indexOf(" -");
            if (hasDash > 0) {
                newLabel = label.substring(0, hasDash);
            }
            finalMap.remove(label);
            finalMap.merge(newLabel + " +", lastCount, Integer::sum);
        }

        return finalMap;
    }
}

package com.provguard.ml;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Isolation Forest trained only on benign feature rows.
 * A higher score means the row looks less like the training families.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IsolationForestModel(Node[] trees, int sampleSize, double scoreLow, double scoreHigh) {
    public static final String RESOURCE = "com/provguard/ml/isolation-forest.json";

    public record Node(int feature, double split, int size, Node left, Node right) {
    }

    public static IsolationForestModel fit(List<double[]> benign, Random random) {
        if (benign.isEmpty()) {
            throw new IllegalArgumentException("Isolation Forest needs benign training rows");
        }
        int sampleSize = Math.min(128, benign.size());
        int heightLimit = (int) Math.ceil(Math.log(sampleSize) / Math.log(2));
        Node[] trees = new Node[100];
        for (int tree = 0; tree < trees.length; tree++) {
            trees[tree] = grow(sample(benign, sampleSize, random), 0, heightLimit, random);
        }
        double[] raw = new double[benign.size()];
        for (int i = 0; i < benign.size(); i++) {
            raw[i] = anomaly(trees, sampleSize, benign.get(i));
        }
        Arrays.sort(raw);
        double low = percentile(raw, 5);
        double high = percentile(raw, 99);
        if (high <= low) {
            high = low + 1.0;
        }
        return new IsolationForestModel(trees, sampleSize, low, high);
    }

    public int score(double[] features) {
        double scaled = (anomaly(trees, sampleSize, features) - scoreLow) / (scoreHigh - scoreLow) * 100.0;
        return (int) Math.max(0, Math.min(100, Math.round(scaled)));
    }

    public void write(OutputStream output) throws IOException {
        new ObjectMapper().writeValue(output, this);
    }

    public static IsolationForestModel read(InputStream input) throws IOException {
        return new ObjectMapper().readValue(input, IsolationForestModel.class);
    }

    public static IsolationForestModel bundled() {
        try (InputStream input = IsolationForestModel.class.getResourceAsStream("/" + RESOURCE)) {
            if (input == null) {
                return null;
            }
            return read(input);
        } catch (IOException exception) {
            return null;
        }
    }

    private static double anomaly(Node[] trees, int sampleSize, double[] features) {
        double depth = 0;
        for (Node tree : trees) {
            depth += path(tree, features, 0);
        }
        depth /= trees.length;
        return Math.pow(2.0, -depth / averagePath(sampleSize));
    }

    private static double path(Node node, double[] features, int depth) {
        if (node.feature < 0 || node.left == null || node.right == null) {
            return depth + averagePath(node.size);
        }
        if (features[node.feature] < node.split) {
            return path(node.left, features, depth + 1);
        }
        return path(node.right, features, depth + 1);
    }

    private static Node grow(List<double[]> rows, int depth, int heightLimit, Random random) {
        if (rows.size() <= 1 || depth >= heightLimit) {
            return new Node(-1, 0, rows.size(), null, null);
        }
        int dimensions = rows.get(0).length;
        int feature = -1;
        double low = 0;
        double high = 0;
        for (int attempt = 0; attempt < dimensions; attempt++) {
            int candidate = random.nextInt(dimensions);
            low = rows.get(0)[candidate];
            high = low;
            for (double[] row : rows) {
                low = Math.min(low, row[candidate]);
                high = Math.max(high, row[candidate]);
            }
            if (high > low) {
                feature = candidate;
                break;
            }
        }
        if (feature < 0) {
            return new Node(-1, 0, rows.size(), null, null);
        }
        double split = low + random.nextDouble() * (high - low);
        List<double[]> left = new ArrayList<>();
        List<double[]> right = new ArrayList<>();
        for (double[] row : rows) {
            if (row[feature] < split) {
                left.add(row);
            } else {
                right.add(row);
            }
        }
        if (left.isEmpty() || right.isEmpty()) {
            return new Node(-1, 0, rows.size(), null, null);
        }
        return new Node(
                feature,
                split,
                rows.size(),
                grow(left, depth + 1, heightLimit, random),
                grow(right, depth + 1, heightLimit, random));
    }

    private static List<double[]> sample(List<double[]> rows, int sampleSize, Random random) {
        List<double[]> copy = new ArrayList<>(rows);
        for (int i = copy.size() - 1; i > 0; i--) {
            int swap = random.nextInt(i + 1);
            double[] row = copy.get(i);
            copy.set(i, copy.get(swap));
            copy.set(swap, row);
        }
        return copy.subList(0, sampleSize);
    }

    static double averagePath(int count) {
        if (count <= 1) {
            return 0;
        }
        if (count == 2) {
            return 1;
        }
        return 2.0 * (Math.log(count - 1) + 0.5772156649) - 2.0 * (count - 1) / count;
    }

    private static double percentile(double[] sorted, int percent) {
        double index = (percent / 100.0) * (sorted.length - 1);
        int lower = (int) Math.floor(index);
        int upper = (int) Math.ceil(index);
        if (lower == upper) {
            return sorted[lower];
        }
        return sorted[lower] + (sorted[upper] - sorted[lower]) * (index - lower);
    }
}

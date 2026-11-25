package com.provguard.demo.safe;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/** Three independent fixture entry points with shared method names for cross-workload comparison. */
public final class OrderedWorkloads {
    private static final int[] CHANGED_ORDER = {0, 2, 1, 3};
    private OrderedWorkloads() {}

    private interface Route {
        void submit(byte[] record, boolean reordered) throws IOException, ClassNotFoundException;
    }

    private static void run(String[] args, String value, Route route) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected nominal, reordered, sequence, or drift");
        }
        byte[] record = serialize(value);
        for (int index = 0; index < 24; index++) {
            switch (args[0]) {
                case "nominal" -> repeat(route, record, false, index % 4);
                case "reordered" -> repeat(route, record, true, index % 4);
                case "sequence" -> repeat(route, record, false, CHANGED_ORDER[index % 4]);
                case "drift" -> review(route, record, index % 4);
                default -> throw new IllegalArgumentException("Unknown scenario: " + args[0]);
            }
        }
    }

    private static void review(Route route, byte[] record, int depth) throws IOException, ClassNotFoundException {
        repeat(route, record, false, depth);
    }

    private static void repeat(Route route, byte[] record, boolean reordered, int depth)
            throws IOException, ClassNotFoundException {
        if (depth == 0) {
            route.submit(record, reordered);
        } else {
            repeat(route, record, reordered, depth - 1);
        }
    }

    private static byte[] serialize(String value) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var output = new ObjectOutputStream(bytes)) {
            output.writeObject(new SafeSerializableObject(value));
        }
        return bytes.toByteArray();
    }

    private static void read(byte[] record) throws IOException, ClassNotFoundException {
        try (var stream = new ObjectInputStream(new ByteArrayInputStream(record))) {
            if (!(stream.readObject() instanceof SafeSerializableObject)) {
                throw new IOException("Unexpected fixture class");
            }
        }
    }

    public static final class Cache implements Route {
        public static void main(String[] args) throws Exception {
            run(args, "cache entry", new Cache());
        }

        public void submit(byte[] record, boolean reordered) throws IOException, ClassNotFoundException {
            if (reordered) {
                decode(record, true);
            } else {
                authorize(record, true);
            }
        }

        private void authorize(byte[] record, boolean first) throws IOException, ClassNotFoundException {
            if (first) decode(record, false);
            else read(record);
        }

        private void decode(byte[] record, boolean first) throws IOException, ClassNotFoundException {
            if (first) authorize(record, false);
            else read(record);
        }
    }

    public static final class Import implements Route {
        public static void main(String[] args) throws Exception {
            run(args, "import item", new Import());
        }

        public void submit(byte[] record, boolean reordered) throws IOException, ClassNotFoundException {
            if (reordered) {
                decode(record, true);
            } else {
                authorize(record, true);
            }
        }

        private void authorize(byte[] record, boolean first) throws IOException, ClassNotFoundException {
            if (first) decode(record, false);
            else read(record);
        }

        private void decode(byte[] record, boolean first) throws IOException, ClassNotFoundException {
            if (first) authorize(record, false);
            else read(record);
        }
    }

    public static final class Replay implements Route {
        public static void main(String[] args) throws Exception {
            run(args, "replay item", new Replay());
        }

        public void submit(byte[] record, boolean reordered) throws IOException, ClassNotFoundException {
            if (reordered) {
                decode(record, true);
            } else {
                authorize(record, true);
            }
        }

        private void authorize(byte[] record, boolean first) throws IOException, ClassNotFoundException {
            if (first) decode(record, false);
            else read(record);
        }

        private void decode(byte[] record, boolean first) throws IOException, ClassNotFoundException {
            if (first) authorize(record, false);
            else read(record);
        }
    }
}

package com.provguard.demo.safe;

import com.provguard.demo.unsafe.BlockedDemoObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/** In-memory fixtures for collecting stack telemetry in Monitor mode. */
public final class StudyWorkload {
    private StudyWorkload() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected training, validation, or test");
        }
        for (int i = 0; i < 60; i++) {
            switch (args[0]) {
                case "training" -> Training.read(i % 6);
                case "validation" -> Validation.read(i % 6);
                case "test" -> Test.read(i % 6);
                default -> throw new IllegalArgumentException("Unknown workload: " + args[0]);
            }
        }
    }

    private static final class Training {
        static void read(int depth) throws IOException, ClassNotFoundException {
            readAtDepth(new SafeSerializableObject("ordinary record"), depth);
        }
    }

    private static final class Validation {
        static void read(int depth) throws IOException, ClassNotFoundException {
            readAtDepth(new SafeSerializableObject("ordinary record"), depth);
        }
    }

    private static final class Test {
        static void read(int depth) throws IOException, ClassNotFoundException {
            // Both classes take the same route so class policy and call depth vary independently.
            readAtDepth(new SafeSerializableObject("ordinary record"), depth);
            readAtDepth(new BlockedDemoObject("harmless policy marker"), depth);
        }
    }

    private static void readAtDepth(Serializable value, int depth) throws IOException, ClassNotFoundException {
        if (depth > 0) {
            readAtDepth(value, depth - 1);
            return;
        }
        var bytes = new ByteArrayOutputStream();
        try (var output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }
        try (var input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            input.readObject();
        }
    }
}

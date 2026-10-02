package com.provguard.demo.safe;

import com.provguard.demo.unsafe.BlockedDemoObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class DeserializationDemo {
    private DeserializationDemo() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && "blocked".equals(args[0])) {
            blocked();
        } else {
            safe();
        }
    }

    public static void safe() throws Exception {
        Object restored = roundTrip(new SafeSerializableObject("allowed"));
        System.out.println("Safe deserialization finished: " + ((SafeSerializableObject) restored).label());
    }

    public static void blocked() throws Exception {
        try {
            roundTrip(new BlockedDemoObject("denied"));
            System.out.println("Blocked demo object deserialized. This mode did not enforce a block.");
        } catch (SecurityException blocked) {
            System.out.println("Blocked demo object was denied: " + blocked.getClass().getSimpleName());
        }
    }

    private static Object roundTrip(Object value) throws IOException, ClassNotFoundException {
        Path file = Files.createTempFile("provguard-demo", ".bin");
        try (var output = new java.io.ObjectOutputStream(Files.newOutputStream(file))) {
            output.writeObject(value);
        }
        try (var input = new java.io.ObjectInputStream(Files.newInputStream(file))) {
            return input.readObject();
        } finally {
            Files.deleteIfExists(file);
        }
    }
}

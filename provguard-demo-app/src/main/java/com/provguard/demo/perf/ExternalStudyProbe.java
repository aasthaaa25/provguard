package com.provguard.demo.perf;

/** Identical harmless target for the external application launch routes. */
public final class ExternalStudyProbe {
    private ExternalStudyProbe() {}

    public static void main(String[] args) {
        if (args.length != 1 || !(args[0].equals("default") || args[0].equals("alternate"))) {
            throw new IllegalArgumentException("Expected a documented probe input");
        }
        System.out.println("PROVGUARD_EXTERNAL_READY " + args[0]);
    }
}

package com.provguard.demo.safe;

public final class SafeDemoApplication {
    private SafeDemoApplication() {
    }

    public static void main(String[] args) throws Exception {
        ProcessExecutionDemo.run();
        DeserializationDemo.safe();
        System.out.println("Safe demo finished");
    }
}

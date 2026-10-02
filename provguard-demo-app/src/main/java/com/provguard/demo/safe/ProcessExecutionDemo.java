package com.provguard.demo.safe;

import com.provguard.demo.LocalCommand;

public final class ProcessExecutionDemo {
    private ProcessExecutionDemo() {
    }

    public static void run() throws Exception {
        Process started = new ProcessBuilder(LocalCommand.echo("provguard-ok")).start();
        started.waitFor();
        Process executed = Runtime.getRuntime().exec(LocalCommand.echoCommand("provguard-runtime-ok"));
        executed.waitFor();
        System.out.println("Safe process execution finished");
    }
}

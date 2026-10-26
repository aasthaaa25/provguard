package com.provguard.demo.suspicious;

import com.provguard.demo.LocalCommand;

import java.lang.reflect.Method;

/**
 * Controlled local demo. The operating-system command is echo.
 * The argument text is intentionally keyword-shaped so the rule engine can score it.
 */
public final class SuspiciousPathDemo {
    private SuspiciousPathDemo() {
    }

    public static void main(String[] args) throws Exception {
        alertPath();
        try {
            Method method = SuspiciousPathDemo.class.getDeclaredMethod("highConfidenceEcho");
            method.invoke(null);
            System.out.println("High-confidence pattern was logged without blocking");
        } catch (java.lang.reflect.InvocationTargetException exception) {
            if (exception.getCause() instanceof SecurityException blocked) {
                System.out.println("High-confidence process call was blocked: " + blocked.getClass().getSimpleName());
            } else {
                throw exception;
            }
        }
    }

    public static void alertPath() throws Exception {
        Process process = new ProcessBuilder(LocalCommand.echo("curl http://example.invalid/file")).start();
        process.waitFor();
        System.out.println("Suspicious alert path finished");
    }

    public static void highConfidenceEcho() throws Exception {
        Process process = new ProcessBuilder(LocalCommand.echo("powershell -enc curl")).start();
        process.waitFor();
    }
}

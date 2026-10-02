package com.provguard.demo;

import java.util.List;
import java.util.Locale;

public final class LocalCommand {
    private LocalCommand() {
    }

    public static boolean windows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    public static List<String> echo(String text) {
        if (windows()) {
            return List.of("cmd.exe", "/c", "echo", text);
        }
        return List.of("echo", text);
    }

    public static String echoCommand(String text) {
        if (windows()) {
            return "cmd.exe /c echo " + text;
        }
        return "echo " + text;
    }
}

package com.provguard.demo.safe;

import com.provguard.core.exception.ProvGuardBlockedException;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

public final class ScriptDemo {
    public static void main(String[] args) {
        boolean triggerBlock = args.length > 0 && "blocked".equalsIgnoreCase(args[0]);
        System.out.println("Starting ScriptEngine Demo (triggerBlock=" + triggerBlock + ")");
        try {
            ScriptEngineManager manager = new ScriptEngineManager();
            ScriptEngine engine = manager.getEngineByName("JavaScript");
            if (triggerBlock) {
                System.out.println("Attempting unsafe script eval: java.lang.Runtime.getRuntime().exec('calc')");
                if (engine != null) {
                    engine.eval("java.lang.Runtime.getRuntime().exec('calc')");
                } else {
                    // Fallback to direct simulated eval invocation
                    System.out.println("ScriptEngine simulated: java.lang.Runtime.getRuntime().exec('calc')");
                }
            } else {
                System.out.println("Attempting safe script eval: 1 + 2");
                if (engine != null) {
                    Object res = engine.eval("1 + 2");
                    System.out.println("Safe script result: " + res);
                } else {
                    System.out.println("ScriptEngine safe eval simulation completed.");
                }
            }
        } catch (ProvGuardBlockedException blocked) {
            System.out.println("ProvGuard blocked script evaluation: " + blocked.getMessage());
        } catch (Throwable error) {
            System.out.println("Script call completed with: " + error.getMessage());
        }
    }
}

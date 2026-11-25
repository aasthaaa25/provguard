package com.provguard.demo.safe;

import javax.script.AbstractScriptEngine;
import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngineFactory;
import javax.script.SimpleBindings;
import java.io.Reader;

/** Exercises the JDK's concrete eval overload with inert fixtures on a stock JDK. */
public final class ScriptDemo {
    private ScriptDemo() {}

    public static void main(String[] args) throws Exception {
        boolean blockFixture = args.length == 1 && "blocked".equals(args[0]);
        String fixture = blockFixture ? "runtime fixture" : "1 + 2";
        try {
            Object result = new FixtureEngine().eval(fixture);
            System.out.println("Script fixture reached the engine: " + result);
        } catch (com.provguard.core.exception.ProvGuardBlockedException blocked) {
            System.out.println("ProvGuard blocked script evaluation: " + blocked.getMessage());
        }
    }

    private static final class FixtureEngine extends AbstractScriptEngine {
        @Override
        public Object eval(String script, ScriptContext context) {
            return "1 + 2".equals(script) ? 3 : "inert policy fixture";
        }

        @Override
        public Object eval(Reader reader, ScriptContext context) {
            throw new UnsupportedOperationException("The fixture accepts String input");
        }

        @Override
        public Bindings createBindings() {
            return new SimpleBindings();
        }

        @Override
        public ScriptEngineFactory getFactory() {
            throw new UnsupportedOperationException("The fixture is constructed directly");
        }
    }
}

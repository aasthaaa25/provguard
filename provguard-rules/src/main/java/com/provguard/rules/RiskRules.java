package com.provguard.rules;

import com.provguard.core.CallFrame;
import com.provguard.core.SecurityEvent;
import com.provguard.core.SinkType;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class RiskRules {
    private RiskRules() {
    }

    public static List<RiskRule> standard(
            Set<String> knownPackages,
            Set<String> knownSignatures,
            Set<String> allowlist,
            Set<String> denylist,
            PathFrequencyRule frequencyRule) {
        List<RiskRule> rules = new ArrayList<>();
        rules.add(new KeywordRule());
        rules.add(new CommandPatternRule());
        rules.add(new LongArgumentRule());
        rules.add(new SpecialCharacterRule());
        rules.add(new ReflectionRule());
        rules.add(new UnknownPathRule(knownPackages, knownSignatures));
        rules.add(frequencyRule);
        rules.add(new DeserializationClassRule(allowlist, denylist));
        rules.add(new ExternalLibraryRule());
        rules.add(new ClassLoaderRule());
        return List.copyOf(rules);
    }

    public static SecurityEvent event(SinkType sinkType) {
        return SecurityEvent.builder()
                .sinkType(sinkType)
                .sinkMethod(sinkType.name())
                .callFrames(List.of(new CallFrame(
                        "com.provguard.demo.safe.SafeDemoApplication",
                        "main",
                        "SafeDemoApplication.java",
                        12,
                        "UNNAMED_MODULE",
                        "app",
                        true)))
                .pathSignature("SafeDemoApplication>" + sinkType.name())
                .pathFamily("com.provguard.demo.safe")
                .stackDepth(1)
                .applicationFrameCount(1)
                .build();
    }
}

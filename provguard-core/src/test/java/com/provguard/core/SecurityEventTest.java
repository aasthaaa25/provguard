package com.provguard.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SecurityEventTest {

    @Test
    void builderCreatesAnImmutableEventAndSignatureRunsOutermostFirst() {
        List<CallFrame> frames = List.of(
                frame("java.lang.Runtime", "exec", false),
                frame("com.provguard.demo.safe.CommandExecutor", "run", true),
                frame("com.provguard.demo.safe.CommandService", "open", true),
                frame("com.provguard.demo.safe.UserController", "handle", true));

        String signature = PathSignatures.signature(frames, "Runtime.exec");
        SecurityEvent event = SecurityEvent.builder()
                .sinkType(SinkType.RUNTIME_EXEC)
                .sinkMethod("Runtime.exec(String)")
                .callFrames(frames)
                .pathSignature(signature)
                .pathFamily(PathSignatures.family(frames))
                .stackDepth(frames.size())
                .build();

        assertEquals("UserController>CommandService>CommandExecutor>Runtime.exec", event.pathSignature());
        assertEquals("com.provguard.demo.safe", event.pathFamily());
        assertThrows(UnsupportedOperationException.class, () -> event.callFrames().add(frames.get(0)));
    }

    @Test
    void riskLevelsFollowTheDocumentedBands() {
        assertEquals(RiskLevel.LOW, RiskLevel.fromScore(0));
        assertEquals(RiskLevel.LOW, RiskLevel.fromScore(29));
        assertEquals(RiskLevel.MEDIUM, RiskLevel.fromScore(30));
        assertEquals(RiskLevel.MEDIUM, RiskLevel.fromScore(59));
        assertEquals(RiskLevel.HIGH, RiskLevel.fromScore(60));
        assertEquals(RiskLevel.HIGH, RiskLevel.fromScore(79));
        assertEquals(RiskLevel.CRITICAL, RiskLevel.fromScore(80));
        assertEquals(RiskLevel.CRITICAL, RiskLevel.fromScore(100));
    }

    private static CallFrame frame(String className, String method, boolean application) {
        return new CallFrame(className, method, "File.java", 10, "UNNAMED_MODULE", "app", application);
    }
}

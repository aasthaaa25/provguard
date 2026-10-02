package com.provguard.agent;

import com.provguard.core.SinkType;
import com.provguard.core.exception.ProvGuardBlockedException;
import com.provguard.core.metadata.ArgumentMetadataCollector;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventPipelineTest {

    @Test
    void monitorModeDoesNotBlockACriticalRuleMatch() throws Exception {
        Path events = Files.createTempFile("provguard-monitor", ".jsonl");
        try (EventPipeline pipeline = EventPipeline.start(AgentConfig.parse(
                "mode=MONITOR;verbose=false;events=" + events.toAbsolutePath()))) {
            pipeline.submit(
                    SinkType.DESERIALIZATION_RESOLVE_CLASS,
                    "ObjectInputStream.resolveClass(ObjectStreamClass)",
                    ArgumentMetadataCollector.fromClassName("com.provguard.demo.unsafe.BlockedDemoObject"));
            assertTrue(pipeline.statistics().blocked() >= 0);
            assertEquals(0, pipeline.statistics().blocked());
            assertEquals(1, pipeline.statistics().captured());
        }
    }

    @Test
    void blockModeBlocksOnlyTheDenylistClass() throws Exception {
        Path events = Files.createTempFile("provguard-block", ".jsonl");
        try (EventPipeline pipeline = EventPipeline.start(AgentConfig.parse(
                "mode=BLOCK;verbose=false;events=" + events.toAbsolutePath()))) {
            pipeline.submit(
                    SinkType.PROCESS_BUILDER_START,
                    "ProcessBuilder.start()",
                    ArgumentMetadataCollector.fromCommandText(1, "String", "echo provguard-ok"));
            assertThrows(ProvGuardBlockedException.class, () -> pipeline.submit(
                    SinkType.DESERIALIZATION_RESOLVE_CLASS,
                    "ObjectInputStream.resolveClass(ObjectStreamClass)",
                    ArgumentMetadataCollector.fromClassName("com.provguard.demo.unsafe.BlockedDemoObject")));
            assertEquals(1, pipeline.statistics().blocked());
        }
    }

    @Test
    void recursionGuardSkipsNestedProcessing() throws Exception {
        Path events = Files.createTempFile("provguard-guard", ".jsonl");
        try (EventPipeline pipeline = EventPipeline.start(AgentConfig.parse(
                "mode=MONITOR;verbose=false;events=" + events.toAbsolutePath()))) {
            RecursionGuard.run(() -> pipeline.onRuntimeExec(new Object[] {"echo nested"}));
            assertEquals(0, pipeline.statistics().captured());
        }
    }

    @Test
    void invalidModeFallsOpenToMonitorDefaults() {
        AgentConfig config = AgentConfig.load("mode=NOT_A_MODE");
        assertEquals(com.provguard.core.AgentMode.MONITOR, config.mode());
    }
}

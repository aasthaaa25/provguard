package com.provguard.core.metadata;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArgumentMetadataTest {

    @Test
    void storesHashAndRedactedPreviewWithoutTheRawSecret() {
        String secret = "supersecretvalue-do-not-store";
        String command = "curl wget http://example.com/file token=" + secret;
        ArgumentSnapshot snapshot = ArgumentMetadataCollector.fromCommandText(1, "String", command);

        assertTrue(snapshot.hash().startsWith("sha256:"));
        assertEquals(64, snapshot.hash().substring("sha256:".length()).length());
        assertEquals(Digests.sha256(command), snapshot.hash());
        assertFalse(snapshot.preview().contains(secret));
        assertTrue(snapshot.preview().endsWith("..."));
        assertTrue(snapshot.urlLike());
        assertEquals(2, snapshot.keywordCount());
        assertEquals(55, snapshot.keywordRiskScore());

        ArgumentSnapshot secretFirst = ArgumentMetadataCollector.fromCommandText(
                1, "String", "token=" + secret);
        assertFalse(secretFirst.preview().contains(secret));
        assertTrue(secretFirst.preview().contains("[REDACTED]"));
    }

    @Test
    void commandPatternDetectsPowerShellEncodedShape() {
        assertTrue(RiskLexicon.commandLike("powershell -enc placeholder"));
        assertFalse(RiskLexicon.commandLike("echo provguard-ok"));
    }

    @Test
    void classNameSnapshotKeepsTheRequestedClassOnly() {
        ArgumentSnapshot snapshot = ArgumentMetadataCollector.fromClassName(
                "com.provguard.demo.unsafe.BlockedDemoObject");
        assertEquals("com.provguard.demo.unsafe.BlockedDemoObject", snapshot.requestedClassName());
        assertEquals(0, snapshot.keywordCount());
    }
}

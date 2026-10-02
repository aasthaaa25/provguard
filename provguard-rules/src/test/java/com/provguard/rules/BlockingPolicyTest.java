package com.provguard.rules;

import com.provguard.core.AgentMode;
import com.provguard.core.Decision;
import com.provguard.core.SecurityEvent;
import com.provguard.core.SinkType;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockingPolicyTest {

    @Test
    void criticalRuleScoreBlocksWithoutAModel() {
        assertEquals(Decision.BLOCK, BlockingPolicy.analytical(80, null));
        assertEquals(Decision.BLOCK, BlockingPolicy.analytical(95, 0));
    }

    @Test
    void highRuleScoreBlocksOnlyWhenTheModelAgrees() {
        assertEquals(Decision.BLOCK, BlockingPolicy.analytical(70, 80));
        assertEquals(Decision.ALERT, BlockingPolicy.analytical(70, 79));
        assertEquals(Decision.ALERT, BlockingPolicy.analytical(60, null));
    }

    @Test
    void modelAloneNeverBlocks() {
        assertEquals(Decision.ALERT, BlockingPolicy.analytical(10, 100));
        assertEquals(Decision.ALERT, BlockingPolicy.analytical(59, 100));
        assertFalse(BlockingPolicy.shouldBlock(AgentMode.BLOCK, Decision.ALERT));
        assertFalse(BlockingPolicy.shouldBlock(AgentMode.MONITOR, Decision.BLOCK));
        assertFalse(BlockingPolicy.shouldBlock(AgentMode.ALERT, Decision.BLOCK));
        assertTrue(BlockingPolicy.shouldBlock(AgentMode.BLOCK, Decision.BLOCK));
    }

    @Test
    void mediumScoresAlertAndLowScoresAllow() {
        assertEquals(Decision.ALERT, BlockingPolicy.analytical(30, 0));
        assertEquals(Decision.ALERT, BlockingPolicy.analytical(59, null));
        assertEquals(Decision.ALLOW, BlockingPolicy.analytical(29, 10));
    }

    @Test
    void alertModeDowngradesABlockDecision() {
        assertEquals(Decision.ALERT, BlockingPolicy.visible(AgentMode.ALERT, Decision.BLOCK));
        assertEquals(Decision.BLOCK, BlockingPolicy.visible(AgentMode.BLOCK, Decision.BLOCK));
    }
}

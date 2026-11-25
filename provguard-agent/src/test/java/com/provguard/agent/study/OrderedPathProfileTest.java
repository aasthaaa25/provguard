package com.provguard.agent.study;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class OrderedPathProfileTest {
    @Test
    void reorderedMethodsRemainDistinctDespiteEqualMultisets() {
        var nominal = path("main", "authorize", "decode", "read");
        var reordered = path("main", "decode", "authorize", "read");
        var profile = new OrderedPathProfile(true, Set.of(nominal));

        assertEquals(nominal.unordered(), reordered.unordered());
        assertEquals("KNOWN", profile.classify(nominal));
        assertEquals("UNSEEN", profile.classify(reordered));
        assertEquals("NO_APPLICATION_PATH", profile.classify(path()));
    }

    @Test
    void virtualPathRetainsOrderedExitsAndEntriesAfterTheCommonPrefix() {
        var previous = path("main", "load", "parse", "read");
        var current = path("main", "load", "decode", "verify");
        var transition = OrderedPathProfile.VirtualPath.between(previous, current);

        assertEquals(2, transition.sharedDepth());
        assertEquals(List.of("read", "parse"), transition.exits());
        assertEquals(List.of("decode", "verify"), transition.entries());
        assertNotEquals(transition, OrderedPathProfile.VirtualPath.between(current, previous));
        assertEquals("START", OrderedPathProfile.VirtualPath.between(null, previous).previousSink());
    }

    private static OrderedPathProfile.MethodPath path(String... methods) {
        return new OrderedPathProfile.MethodPath("DESERIALIZATION_RESOLVE_CLASS", List.of(methods));
    }
}

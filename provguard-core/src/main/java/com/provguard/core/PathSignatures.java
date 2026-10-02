package com.provguard.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a call-path signature from stack frames. The result is a trace, not a provenance graph.
 */
public final class PathSignatures {
    private PathSignatures() {
    }

    public static String signature(List<CallFrame> frames, String sinkLabel) {
        List<CallFrame> application = new ArrayList<>();
        for (CallFrame frame : frames) {
            if (frame.applicationClass()) {
                application.add(frame);
            }
        }
        int start = Math.max(0, application.size() - 12);
        StringBuilder builder = new StringBuilder();
        for (int i = application.size() - 1; i >= start; i--) {
            if (builder.length() > 0) {
                builder.append('>');
            }
            builder.append(application.get(i).simpleClassName());
        }
        if (builder.length() > 0) {
            builder.append('>');
        }
        builder.append(sinkLabel);
        return builder.toString();
    }

    public static String family(List<CallFrame> frames) {
        for (int i = frames.size() - 1; i >= 0; i--) {
            CallFrame frame = frames.get(i);
            if (frame.applicationClass() && !frame.packageName().isBlank()) {
                return frame.packageName();
            }
        }
        return "no-application-frame";
    }
}

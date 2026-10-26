package com.provguard.core;

import com.provguard.core.metadata.ArgumentSnapshot;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Factory for sink-specific security events. Callers supply a stack-based trace and safe metadata.
 */
public final class SecurityEventFactory {
    private SecurityEventFactory() {
    }

    public static SecurityEvent runtimeExec(
            String sinkMethod,
            List<CallFrame> frames,
            String pathSignature,
            String pathFamily,
            StackCounts counts,
            ArgumentSnapshot arguments,
            int pathFrequency) {
        return create(SinkType.RUNTIME_EXEC, sinkMethod, frames, pathSignature, pathFamily, counts, arguments, pathFrequency);
    }

    public static SecurityEvent processBuilder(
            String sinkMethod,
            List<CallFrame> frames,
            String pathSignature,
            String pathFamily,
            StackCounts counts,
            ArgumentSnapshot arguments,
            int pathFrequency) {
        return create(SinkType.PROCESS_BUILDER_START, sinkMethod, frames, pathSignature, pathFamily, counts, arguments, pathFrequency);
    }

    public static SecurityEvent deserialization(
            String sinkMethod,
            List<CallFrame> frames,
            String pathSignature,
            String pathFamily,
            StackCounts counts,
            ArgumentSnapshot arguments,
            int pathFrequency) {
        return create(
                SinkType.DESERIALIZATION_RESOLVE_CLASS,
                sinkMethod,
                frames,
                pathSignature,
                pathFamily,
                counts,
                arguments,
                pathFrequency);
    }

    private static SecurityEvent create(
            SinkType sinkType,
            String sinkMethod,
            List<CallFrame> frames,
            String pathSignature,
            String pathFamily,
            StackCounts counts,
            ArgumentSnapshot arguments,
            int pathFrequency) {
        Thread thread = Thread.currentThread();
        return SecurityEvent.builder()
                .eventId(UUID.randomUUID())
                .timestamp(Instant.now())
                .sinkType(sinkType)
                .sinkMethod(sinkMethod)
                .threadName(thread.getName())
                .threadId(thread.threadId())
                .stackDepth(counts.stackDepth())
                .callFrames(frames)
                .pathSignature(pathSignature)
                .pathFamily(pathFamily)
                .argumentCount(arguments.count())
                .argumentType(arguments.type())
                .argumentLength(arguments.length())
                .argumentHash(arguments.hash())
                .redactedArgumentPreview(arguments.preview())
                .suspiciousKeywordCount(arguments.keywordCount())
                .keywordRiskScore(arguments.keywordRiskScore())
                .specialCharacterCount(arguments.specialCharacterCount())
                .urlLike(arguments.urlLike())
                .commandLike(arguments.commandLike())
                .nullArgument(arguments.nullArgument())
                .reflectionDetected(counts.reflectionDetected())
                .applicationFrameCount(counts.applicationFrameCount())
                .jdkFrameCount(counts.jdkFrameCount())
                .externalLibraryFrameCount(counts.externalLibraryFrameCount())
                .uniquePackageCount(counts.uniquePackageCount())
                .classLoaderCount(counts.classLoaderCount())
                .pathFrequency(pathFrequency)
                .requestedClassName(arguments.requestedClassName())
                .build();
    }

    public record StackCounts(
            int stackDepth,
            int applicationFrameCount,
            int jdkFrameCount,
            int externalLibraryFrameCount,
            int uniquePackageCount,
            int classLoaderCount,
            boolean reflectionDetected) {
    }
}

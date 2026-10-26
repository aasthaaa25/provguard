package com.provguard.core;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable record of one monitored API call.
 * Argument fields store a hash, length, and redacted preview. The raw argument is not retained.
 */
public final class SecurityEvent {
    private final UUID eventId;
    private final Instant timestamp;
    private final SinkType sinkType;
    private final String sinkMethod;
    private final String threadName;
    private final long threadId;
    private final int stackDepth;
    private final List<CallFrame> callFrames;
    private final String pathSignature;
    private final String pathFamily;
    private final int argumentCount;
    private final String argumentType;
    private final int argumentLength;
    private final String argumentHash;
    private final String redactedArgumentPreview;
    private final int suspiciousKeywordCount;
    private final int keywordRiskScore;
    private final int specialCharacterCount;
    private final boolean urlLike;
    private final boolean commandLike;
    private final boolean nullArgument;
    private final boolean reflectionDetected;
    private final int applicationFrameCount;
    private final int jdkFrameCount;
    private final int externalLibraryFrameCount;
    private final int uniquePackageCount;
    private final int classLoaderCount;
    private final int pathFrequency;
    private final boolean queueDropped;
    private final String requestedClassName;

    private SecurityEvent(Builder builder) {
        this.eventId = builder.eventId;
        this.timestamp = builder.timestamp;
        this.sinkType = builder.sinkType;
        this.sinkMethod = builder.sinkMethod;
        this.threadName = builder.threadName;
        this.threadId = builder.threadId;
        this.stackDepth = builder.stackDepth;
        this.callFrames = List.copyOf(builder.callFrames);
        this.pathSignature = builder.pathSignature;
        this.pathFamily = builder.pathFamily;
        this.argumentCount = builder.argumentCount;
        this.argumentType = builder.argumentType;
        this.argumentLength = builder.argumentLength;
        this.argumentHash = builder.argumentHash;
        this.redactedArgumentPreview = builder.redactedArgumentPreview;
        this.suspiciousKeywordCount = builder.suspiciousKeywordCount;
        this.keywordRiskScore = builder.keywordRiskScore;
        this.specialCharacterCount = builder.specialCharacterCount;
        this.urlLike = builder.urlLike;
        this.commandLike = builder.commandLike;
        this.nullArgument = builder.nullArgument;
        this.reflectionDetected = builder.reflectionDetected;
        this.applicationFrameCount = builder.applicationFrameCount;
        this.jdkFrameCount = builder.jdkFrameCount;
        this.externalLibraryFrameCount = builder.externalLibraryFrameCount;
        this.uniquePackageCount = builder.uniquePackageCount;
        this.classLoaderCount = builder.classLoaderCount;
        this.pathFrequency = builder.pathFrequency;
        this.queueDropped = builder.queueDropped;
        this.requestedClassName = builder.requestedClassName;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
                .eventId(eventId)
                .timestamp(timestamp)
                .sinkType(sinkType)
                .sinkMethod(sinkMethod)
                .threadName(threadName)
                .threadId(threadId)
                .stackDepth(stackDepth)
                .callFrames(callFrames)
                .pathSignature(pathSignature)
                .pathFamily(pathFamily)
                .argumentCount(argumentCount)
                .argumentType(argumentType)
                .argumentLength(argumentLength)
                .argumentHash(argumentHash)
                .redactedArgumentPreview(redactedArgumentPreview)
                .suspiciousKeywordCount(suspiciousKeywordCount)
                .keywordRiskScore(keywordRiskScore)
                .specialCharacterCount(specialCharacterCount)
                .urlLike(urlLike)
                .commandLike(commandLike)
                .nullArgument(nullArgument)
                .reflectionDetected(reflectionDetected)
                .applicationFrameCount(applicationFrameCount)
                .jdkFrameCount(jdkFrameCount)
                .externalLibraryFrameCount(externalLibraryFrameCount)
                .uniquePackageCount(uniquePackageCount)
                .classLoaderCount(classLoaderCount)
                .pathFrequency(pathFrequency)
                .queueDropped(queueDropped)
                .requestedClassName(requestedClassName);
    }

    public UUID eventId() {
        return eventId;
    }

    public Instant timestamp() {
        return timestamp;
    }

    public SinkType sinkType() {
        return sinkType;
    }

    public String sinkMethod() {
        return sinkMethod;
    }

    public String threadName() {
        return threadName;
    }

    public long threadId() {
        return threadId;
    }

    public int stackDepth() {
        return stackDepth;
    }

    public List<CallFrame> callFrames() {
        return callFrames;
    }

    public String pathSignature() {
        return pathSignature;
    }

    public String pathFamily() {
        return pathFamily;
    }

    public int argumentCount() {
        return argumentCount;
    }

    public String argumentType() {
        return argumentType;
    }

    public int argumentLength() {
        return argumentLength;
    }

    public String argumentHash() {
        return argumentHash;
    }

    public String redactedArgumentPreview() {
        return redactedArgumentPreview;
    }

    public int suspiciousKeywordCount() {
        return suspiciousKeywordCount;
    }

    public int keywordRiskScore() {
        return keywordRiskScore;
    }

    public int specialCharacterCount() {
        return specialCharacterCount;
    }

    public boolean urlLike() {
        return urlLike;
    }

    public boolean commandLike() {
        return commandLike;
    }

    public boolean nullArgument() {
        return nullArgument;
    }

    public boolean reflectionDetected() {
        return reflectionDetected;
    }

    public int applicationFrameCount() {
        return applicationFrameCount;
    }

    public int jdkFrameCount() {
        return jdkFrameCount;
    }

    public int externalLibraryFrameCount() {
        return externalLibraryFrameCount;
    }

    public int uniquePackageCount() {
        return uniquePackageCount;
    }

    public int classLoaderCount() {
        return classLoaderCount;
    }

    public int pathFrequency() {
        return pathFrequency;
    }

    public boolean queueDropped() {
        return queueDropped;
    }

    public String requestedClassName() {
        return requestedClassName;
    }

    public static final class Builder {
        private UUID eventId;
        private Instant timestamp;
        private SinkType sinkType;
        private String sinkMethod;
        private String threadName;
        private long threadId = Thread.currentThread().threadId();
        private int stackDepth;
        private List<CallFrame> callFrames = List.of();
        private String pathSignature = "";
        private String pathFamily = "unspecified";
        private int argumentCount;
        private String argumentType = "unknown";
        private int argumentLength;
        private String argumentHash = "sha256:";
        private String redactedArgumentPreview = "";
        private int suspiciousKeywordCount;
        private int keywordRiskScore;
        private int specialCharacterCount;
        private boolean urlLike;
        private boolean commandLike;
        private boolean nullArgument;
        private boolean reflectionDetected;
        private int applicationFrameCount;
        private int jdkFrameCount;
        private int externalLibraryFrameCount;
        private int uniquePackageCount;
        private int classLoaderCount;
        private int pathFrequency;
        private boolean queueDropped;
        private String requestedClassName = "";

        public Builder eventId(UUID eventId) {
            this.eventId = eventId;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder sinkType(SinkType sinkType) {
            this.sinkType = sinkType;
            return this;
        }

        public Builder sinkMethod(String sinkMethod) {
            this.sinkMethod = sinkMethod;
            return this;
        }

        public Builder threadName(String threadName) {
            this.threadName = threadName;
            return this;
        }

        public Builder threadId(long threadId) {
            this.threadId = threadId;
            return this;
        }

        public Builder stackDepth(int stackDepth) {
            this.stackDepth = stackDepth;
            return this;
        }

        public Builder callFrames(List<CallFrame> callFrames) {
            this.callFrames = callFrames == null ? List.of() : callFrames;
            return this;
        }

        public Builder pathSignature(String pathSignature) {
            this.pathSignature = pathSignature;
            return this;
        }

        public Builder pathFamily(String pathFamily) {
            this.pathFamily = pathFamily;
            return this;
        }

        public Builder argumentCount(int argumentCount) {
            this.argumentCount = argumentCount;
            return this;
        }

        public Builder argumentType(String argumentType) {
            this.argumentType = argumentType;
            return this;
        }

        public Builder argumentLength(int argumentLength) {
            this.argumentLength = argumentLength;
            return this;
        }

        public Builder argumentHash(String argumentHash) {
            this.argumentHash = argumentHash;
            return this;
        }

        public Builder redactedArgumentPreview(String redactedArgumentPreview) {
            this.redactedArgumentPreview = redactedArgumentPreview;
            return this;
        }

        public Builder suspiciousKeywordCount(int suspiciousKeywordCount) {
            this.suspiciousKeywordCount = suspiciousKeywordCount;
            return this;
        }

        public Builder keywordRiskScore(int keywordRiskScore) {
            this.keywordRiskScore = keywordRiskScore;
            return this;
        }

        public Builder specialCharacterCount(int specialCharacterCount) {
            this.specialCharacterCount = specialCharacterCount;
            return this;
        }

        public Builder urlLike(boolean urlLike) {
            this.urlLike = urlLike;
            return this;
        }

        public Builder commandLike(boolean commandLike) {
            this.commandLike = commandLike;
            return this;
        }

        public Builder nullArgument(boolean nullArgument) {
            this.nullArgument = nullArgument;
            return this;
        }

        public Builder reflectionDetected(boolean reflectionDetected) {
            this.reflectionDetected = reflectionDetected;
            return this;
        }

        public Builder applicationFrameCount(int applicationFrameCount) {
            this.applicationFrameCount = applicationFrameCount;
            return this;
        }

        public Builder jdkFrameCount(int jdkFrameCount) {
            this.jdkFrameCount = jdkFrameCount;
            return this;
        }

        public Builder externalLibraryFrameCount(int externalLibraryFrameCount) {
            this.externalLibraryFrameCount = externalLibraryFrameCount;
            return this;
        }

        public Builder uniquePackageCount(int uniquePackageCount) {
            this.uniquePackageCount = uniquePackageCount;
            return this;
        }

        public Builder classLoaderCount(int classLoaderCount) {
            this.classLoaderCount = classLoaderCount;
            return this;
        }

        public Builder pathFrequency(int pathFrequency) {
            this.pathFrequency = pathFrequency;
            return this;
        }

        public Builder queueDropped(boolean queueDropped) {
            this.queueDropped = queueDropped;
            return this;
        }

        public Builder requestedClassName(String requestedClassName) {
            this.requestedClassName = requestedClassName;
            return this;
        }

        public SecurityEvent build() {
            if (sinkType == null) {
                throw new IllegalArgumentException("sinkType is required");
            }
            if (eventId == null) {
                eventId = UUID.randomUUID();
            }
            if (timestamp == null) {
                timestamp = Instant.now();
            }
            if (sinkMethod == null || sinkMethod.isBlank()) {
                sinkMethod = sinkType.name();
            }
            if (threadName == null || threadName.isBlank()) {
                threadName = Thread.currentThread().getName();
            }
            pathSignature = Objects.requireNonNullElse(pathSignature, "");
            pathFamily = pathFamily == null || pathFamily.isBlank() ? "unspecified" : pathFamily;
            argumentType = argumentType == null ? "unknown" : argumentType;
            argumentHash = argumentHash == null ? "sha256:" : argumentHash;
            redactedArgumentPreview = redactedArgumentPreview == null ? "" : redactedArgumentPreview;
            requestedClassName = requestedClassName == null ? "" : requestedClassName;
            return new SecurityEvent(this);
        }
    }
}

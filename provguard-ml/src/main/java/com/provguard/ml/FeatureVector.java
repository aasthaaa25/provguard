package com.provguard.ml;

import com.provguard.core.SecurityEvent;

/**
 * Numerical features used by the Java Isolation Forest.
 */
public record FeatureVector(
        double stackDepth,
        double applicationFrameCount,
        double jdkFrameCount,
        double externalLibraryFrameCount,
        double uniquePackageCount,
        double reflectionDetected,
        double argumentLength,
        double keywordRiskScore,
        double pathFrequency,
        double sinkTypeEncoded,
        double classLoaderCount,
        double ruleScore) {

    public static FeatureVector from(SecurityEvent event, int ruleScore) {
        return new FeatureVector(
                event.stackDepth(),
                event.applicationFrameCount(),
                event.jdkFrameCount(),
                event.externalLibraryFrameCount(),
                event.uniquePackageCount(),
                event.reflectionDetected() ? 1 : 0,
                event.argumentLength(),
                event.keywordRiskScore(),
                event.pathFrequency(),
                event.sinkType().encoded(),
                event.classLoaderCount(),
                ruleScore);
    }

    public double[] values() {
        return new double[] {
                stackDepth,
                applicationFrameCount,
                jdkFrameCount,
                externalLibraryFrameCount,
                uniquePackageCount,
                reflectionDetected,
                argumentLength,
                keywordRiskScore,
                pathFrequency,
                sinkTypeEncoded,
                classLoaderCount,
                ruleScore
        };
    }

    public String toJson() {
        return "{\"stack_depth\":" + stackDepth
                + ",\"application_frame_count\":" + applicationFrameCount
                + ",\"jdk_frame_count\":" + jdkFrameCount
                + ",\"external_library_frame_count\":" + externalLibraryFrameCount
                + ",\"unique_package_count\":" + uniquePackageCount
                + ",\"reflection_detected\":" + reflectionDetected
                + ",\"argument_length\":" + argumentLength
                + ",\"keyword_risk_score\":" + keywordRiskScore
                + ",\"path_frequency\":" + pathFrequency
                + ",\"sink_type_encoded\":" + sinkTypeEncoded
                + ",\"class_loader_count\":" + classLoaderCount
                + ",\"rule_score\":" + ruleScore
                + "}";
    }
}

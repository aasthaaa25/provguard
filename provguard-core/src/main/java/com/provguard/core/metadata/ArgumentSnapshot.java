package com.provguard.core.metadata;

/**
 * Safe argument metadata. The original text is not part of this object.
 */
public record ArgumentSnapshot(
        int count,
        String type,
        int length,
        String hash,
        String preview,
        int keywordCount,
        int keywordRiskScore,
        int specialCharacterCount,
        boolean urlLike,
        boolean commandLike,
        boolean nullArgument,
        String requestedClassName) {

    public ArgumentSnapshot {
        type = type == null ? "unknown" : type;
        hash = hash == null ? "sha256:" : hash;
        preview = preview == null ? "" : preview;
        requestedClassName = requestedClassName == null ? "" : requestedClassName;
    }
}

package com.provguard.core.metadata;

import java.util.StringJoiner;

public final class ArgumentMetadataCollector {
    private ArgumentMetadataCollector() {
    }

    public static ArgumentSnapshot fromCommandText(int argumentCount, String type, String commandText) {
        boolean nullArgument = commandText == null;
        String value = commandText == null ? "" : commandText;
        int keywords = RiskLexicon.keywordCount(value);
        return new ArgumentSnapshot(
                argumentCount,
                type,
                value.length(),
                Digests.sha256(value),
                RiskLexicon.redact(value),
                keywords,
                RiskLexicon.keywordRiskScore(keywords),
                RiskLexicon.specialCharacterCount(value),
                RiskLexicon.urlLike(value),
                RiskLexicon.commandLike(value),
                nullArgument,
                "");
    }

    public static ArgumentSnapshot fromClassName(String className) {
        String value = className == null ? "" : className;
        return new ArgumentSnapshot(
                className == null ? 0 : 1,
                "ObjectStreamClass",
                value.length(),
                Digests.sha256(value),
                value,
                0,
                0,
                0,
                false,
                false,
                className == null,
                value);
    }

    public static String joinCommand(String[] command) {
        if (command == null) {
            return null;
        }
        StringJoiner joiner = new StringJoiner(" ");
        for (String part : command) {
            joiner.add(part == null ? "" : part);
        }
        return joiner.toString();
    }
}

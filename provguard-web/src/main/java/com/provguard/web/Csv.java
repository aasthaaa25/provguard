package com.provguard.web;

/** CSV export helpers. Cells that a spreadsheet would read as a formula get a leading apostrophe. */
final class Csv {
    private Csv() {
    }

    static String header() {
        return "id,timestamp,sinkType,sinkMethod,mode,ruleScore,mlScore,finalScore,riskLevel,decision,policyDecision,"
                + "enforced,outcome,modelStatus,matchedRules,entry,pathSignature,threadName\r\n";
    }

    static String row(EventSummary e) {
        StringBuilder row = new StringBuilder();
        cell(row, e.id());
        cell(row, e.timestamp());
        cell(row, e.sinkType());
        cell(row, e.sinkMethod());
        cell(row, e.mode());
        cell(row, e.ruleScore());
        cell(row, e.mlScore());
        cell(row, e.finalScore());
        cell(row, e.riskLevel());
        cell(row, e.decision());
        cell(row, e.policyDecision());
        cell(row, e.enforced());
        cell(row, e.outcome());
        cell(row, e.modelStatus());
        cell(row, String.join(";", e.matchedRules()));
        cell(row, e.entry());
        cell(row, e.pathSignature());
        row.append(escape(e.threadName())).append("\r\n");
        return row.toString();
    }

    private static void cell(StringBuilder row, Object value) {
        row.append(escape(value == null ? "" : String.valueOf(value))).append(',');
    }

    static String escape(String value) {
        String text = value == null ? "" : value;
        if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}

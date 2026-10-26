package com.provguard.storage;

import com.provguard.core.AgentMode;
import com.provguard.core.CallFrame;
import com.provguard.core.RiskResult;
import com.provguard.core.SecurityEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class JsonEventMapper {
    public Map<String, Object> toMap(SecurityEvent event, RiskResult result, AgentMode mode) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("schemaVersion", 1);
        json.put("eventId", event.eventId().toString());
        json.put("timestamp", event.timestamp().toString());
        json.put("sinkType", event.sinkType().name());
        json.put("sinkMethod", event.sinkMethod());
        json.put("threadName", event.threadName());
        json.put("threadId", event.threadId());
        json.put("stackDepth", event.stackDepth());
        json.put("pathSignature", event.pathSignature());
        json.put("pathFamily", event.pathFamily());
        json.put("argumentCount", event.argumentCount());
        json.put("argumentType", event.argumentType());
        json.put("argumentLength", event.argumentLength());
        json.put("argumentHash", event.argumentHash());
        json.put("redactedArgumentPreview", event.redactedArgumentPreview());
        json.put("suspiciousKeywordCount", event.suspiciousKeywordCount());
        json.put("keywordRiskScore", event.keywordRiskScore());
        json.put("specialCharacterCount", event.specialCharacterCount());
        json.put("urlLike", event.urlLike());
        json.put("commandLike", event.commandLike());
        json.put("nullArgument", event.nullArgument());
        json.put("reflectionDetected", event.reflectionDetected());
        json.put("applicationFrameCount", event.applicationFrameCount());
        json.put("jdkFrameCount", event.jdkFrameCount());
        json.put("externalLibraryFrameCount", event.externalLibraryFrameCount());
        json.put("uniquePackageCount", event.uniquePackageCount());
        json.put("classLoaderCount", event.classLoaderCount());
        json.put("pathFrequency", event.pathFrequency());
        json.put("queueDropped", event.queueDropped());
        json.put("requestedClassName", event.requestedClassName());
        json.put("callFrames", frames(event));
        json.put("ruleScore", result.ruleScore());
        json.put("mlScore", result.mlScore());
        json.put("finalScore", result.finalScore());
        json.put("riskLevel", result.riskLevel().name());
        json.put("decision", result.decision().name());
        json.put("policyDecision", result.policyDecision().name());
        json.put("enforced", result.enforced());
        json.put("matchedRules", result.matchedRules());
        json.put("modelStatus", result.modelStatus());
        json.put("explanation", result.explanation());
        json.put("mode", mode.name());
        return json;
    }

    private List<Map<String, Object>> frames(SecurityEvent event) {
        List<Map<String, Object>> frames = new ArrayList<>();
        for (CallFrame frame : event.callFrames()) {
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("className", frame.className());
            json.put("methodName", frame.methodName());
            json.put("fileName", frame.fileName());
            json.put("lineNumber", frame.lineNumber());
            json.put("moduleName", frame.moduleName());
            json.put("classLoaderName", frame.classLoaderName());
            json.put("applicationClass", frame.applicationClass());
            frames.add(json);
        }
        return frames;
    }
}

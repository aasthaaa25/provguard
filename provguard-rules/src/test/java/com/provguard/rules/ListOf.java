package com.provguard.rules;

import com.provguard.core.CallFrame;

import java.util.List;

final class ListOf {
    private ListOf() {
    }

    static List<CallFrame> suspicious() {
        return List.of(new CallFrame(
                "com.provguard.demo.suspicious.SuspiciousPathDemo",
                "main",
                "SuspiciousPathDemo.java",
                20,
                "UNNAMED_MODULE",
                "app",
                true));
    }

    static List<CallFrame> unsafe() {
        return List.of(new CallFrame(
                "com.provguard.demo.unsafe.DeserializationDemo",
                "main",
                "DeserializationDemo.java",
                30,
                "UNNAMED_MODULE",
                "app",
                true));
    }
}

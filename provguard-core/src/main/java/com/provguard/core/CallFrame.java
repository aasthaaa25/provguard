package com.provguard.core;

import java.util.Objects;

/**
 * One frame from a stack-based call-path trace.
 * This is not a node in a complete method-call graph.
 */
public final class CallFrame {
    private final String className;
    private final String methodName;
    private final String fileName;
    private final int lineNumber;
    private final String moduleName;
    private final String classLoaderName;
    private final boolean applicationClass;

    public CallFrame(
            String className,
            String methodName,
            String fileName,
            int lineNumber,
            String moduleName,
            String classLoaderName,
            boolean applicationClass) {
        this.className = Objects.requireNonNull(className, "className");
        this.methodName = Objects.requireNonNull(methodName, "methodName");
        this.fileName = fileName == null ? "" : fileName;
        this.lineNumber = lineNumber;
        this.moduleName = moduleName == null || moduleName.isBlank() ? "UNKNOWN_MODULE" : moduleName;
        this.classLoaderName = classLoaderName == null || classLoaderName.isBlank()
                ? "UNKNOWN_CLASS_LOADER"
                : classLoaderName;
        this.applicationClass = applicationClass;
    }

    public String className() {
        return className;
    }

    public String methodName() {
        return methodName;
    }

    public String fileName() {
        return fileName;
    }

    public int lineNumber() {
        return lineNumber;
    }

    public String moduleName() {
        return moduleName;
    }

    public String classLoaderName() {
        return classLoaderName;
    }

    public boolean applicationClass() {
        return applicationClass;
    }

    public String simpleClassName() {
        int dot = className.lastIndexOf('.');
        return dot < 0 ? className : className.substring(dot + 1);
    }

    public String packageName() {
        int dot = className.lastIndexOf('.');
        return dot < 0 ? "" : className.substring(0, dot);
    }
}

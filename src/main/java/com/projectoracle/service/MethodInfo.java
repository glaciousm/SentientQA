package com.projectoracle.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Contains detailed information about a Java method extracted from code analysis.
 * Used as input for test generation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MethodInfo {

    private String packageName;
    private String className;
    private String methodName;
    private String returnType;
    private List<ParameterInfo> parameters;
    private List<String> exceptions;
    private String javadoc;
    private boolean isPublic;
    private boolean isStatic;
    private String body;

    /**
     * Get the fully qualified name of the method
     */
    public String getFullyQualifiedName() {
        return packageName + "." + className + "." + methodName;
    }

    /**
     * Get a simple signature of the method
     */
    public String getSignature() {
        StringBuilder sb = new StringBuilder();
        sb.append(methodName).append("(");

        if (parameters != null && !parameters.isEmpty()) {
            sb.append(parameters.stream()
                                .map(param -> param.getType() + " " + param.getName())
                                .collect(Collectors.joining(", ")));
        }

        sb.append(")");
        return sb.toString();
    }
}

/**
 * Contains information about a method parameter
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
class ParameterInfo {
    private String type;
    private String name;
}
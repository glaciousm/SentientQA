package com.projectoracle.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Request object for knowledge integration with test generation.
 * Contains the method signature to generate tests for and a list of
 * knowledge sources to integrate.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeIntegrationRequest {
    private String methodSignature;
    private List<KnowledgeSource> knowledgeSources = new ArrayList<>();
}
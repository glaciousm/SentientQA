package com.projectoracle.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a knowledge source for integration with test generation.
 * Knowledge sources include API documentation, project documentation,
 * historical test data, and source code with comments.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeSource {
    private String type;        // Type of knowledge source (api, docs, history, source)
    private String path;        // Path to the knowledge source
    private String format;      // Format of the source (e.g., swagger, markdown, json)
    private boolean enabled;    // Whether this source should be used
}
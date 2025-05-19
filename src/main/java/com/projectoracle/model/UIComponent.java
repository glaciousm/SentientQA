package com.projectoracle.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import lombok.Data;

/**
 * Represents an interactive UI component discovered during web application crawling.
 * Contains information about the component's properties and behavior.
 */
@Data
public class UIComponent {

    private String type;           // e.g., "button", "input", "select", "link"
    private String subtype;        // e.g., "text", "checkbox", "radio" for input type
    private String name;           // Name attribute or text content
    private String id;             // ID attribute
    private String className;      // CSS class
    private String elementLocator; // XPath or CSS selector
    private ElementFingerprint fingerprint; // Unique fingerprint for test healing
    private List<String> options;  // For select elements
    private int childCount;        // Number of child elements
    private String formId;         // ID of parent form if applicable
    private boolean required;      // Whether the field is required
    private String validationPattern; // Validation pattern if available

    /**
     * Add an option to the component
     *
     * @param option the option to add
     */
    public void addOption(String option) {
        if (options == null) {
            options = new ArrayList<>();
        }
        options.add(option);
    }

    /**
     * Generate a test value for this component based on its type
     *
     * @return a test value appropriate for this component
     */
    public String generateTestValue() {
        if ("input".equals(type)) {
            switch (subtype) {
                case "text":
                    return "Test " + (name != null ? name : "Input");
                case "email":
                    return "test@example.com";
                case "password":
                    return "Password123!";
                case "number":
                    return "42";
                case "date":
                    return "2023-06-15";
                case "checkbox":
                    return "true";
                case "radio":
                    return "option1";
                case "tel":
                    return "555-123-4567";
                case "url":
                    return "https://example.com";
                default:
                    return "Test Value";
            }
        } else if ("select".equals(type) && options != null && !options.isEmpty()) {
            return options.get(0);
        } else if ("textarea".equals(type)) {
            return "This is a test comment or description. It contains multiple sentences to provide realistic content for testing purposes.";
        } else {
            return "Test Value";
        }
    }

    /**
     * Determine if this component is an interactive element
     *
     * @return true if the component is interactive
     */
    public boolean isInteractive() {
        return "button".equals(type) ||
                "input".equals(type) ||
                "select".equals(type) ||
                "textarea".equals(type) ||
                "interactive".equals(type) ||
                "link".equals(type);
    }

    /**
     * Create a human-readable description of this component
     *
     * @return a description string
     */
    public String getDescription() {
        StringBuilder sb = new StringBuilder();

        sb.append(type);

        if (subtype != null && !subtype.isEmpty()) {
            sb.append(" (").append(subtype).append(")");
        }

        if (name != null && !name.isEmpty()) {
            sb.append(": ").append(name);
        } else if (id != null && !id.isEmpty()) {
            sb.append(" id=").append(id);
        }

        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UIComponent component = (UIComponent) o;
        return Objects.equals(elementLocator, component.elementLocator);
    }

    @Override
    public int hashCode() {
        return Objects.hash(elementLocator);
    }
}
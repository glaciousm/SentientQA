package com.projectoracle.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import lombok.Data;

/**
 * Represents a web page discovered by the UI crawler.
 * Contains information about the page structure and interactive elements.
 */
@Data
public class Page {

    private java.util.UUID id = java.util.UUID.randomUUID();
    private String url;
    private String title;
    private String description;
    private long discoveryTimestamp;
    private List<UIComponent> components = new ArrayList<>();
    private List<String> linkedUrls = new ArrayList<>();
    private boolean analyzed;
    private PageType pageType;

    /**
     * Types of web pages based on their purpose/structure
     */
    public enum PageType {
        LANDING,
        LOGIN,
        FORM,
        LISTING,
        DETAIL,
        DASHBOARD,
        ERROR,
        UNKNOWN
    }

    /**
     * Add a component to the page
     *
     * @param component the component to add
     */
    public void addComponent(UIComponent component) {
        if (components == null) {
            components = new ArrayList<>();
        }
        components.add(component);
    }

    /**
     * Add a linked URL to the page
     *
     * @param url the linked URL to add
     */
    public void addLinkedUrl(String url) {
        if (linkedUrls == null) {
            linkedUrls = new ArrayList<>();
        }
        linkedUrls.add(url);
    }

    /**
     * Determine the page type based on component analysis
     */
    public void determinePageType() {
        if (components == null || components.isEmpty()) {
            pageType = PageType.UNKNOWN;
            return;
        }

        int formCount = 0;
        int inputCount = 0;
        boolean hasLoginInputs = false;
        boolean hasPasswordField = false;
        boolean hasLargeTable = false;
        boolean hasCharts = false;

        for (UIComponent component : components) {
            if ("form".equals(component.getType())) {
                formCount++;
            } else if ("input".equals(component.getType())) {
                inputCount++;

                if ("username".equals(component.getName()) ||
                        "email".equals(component.getName()) ||
                        "user".equals(component.getName()) ||
                        "login".equals(component.getName())) {
                    hasLoginInputs = true;
                }

                if ("password".equals(component.getSubtype()) ||
                        "password".equals(component.getName())) {
                    hasPasswordField = true;
                }
            } else if ("table".equals(component.getType()) && component.getChildCount() > 10) {
                hasLargeTable = true;
            } else if ("chart".equals(component.getType()) || 
                      (component.getClassName() != null && component.getClassName().contains("chart"))) {
                hasCharts = true;
            }
        }

        // Determine page type based on components
        if (hasLoginInputs && hasPasswordField) {
            pageType = PageType.LOGIN;
        } else if (formCount > 0 && inputCount > 3) {
            pageType = PageType.FORM;
        } else if (hasLargeTable) {
            pageType = PageType.LISTING;
        } else if (hasCharts || components.size() > 15) {
            pageType = PageType.DASHBOARD;
        } else if (url.contains("error") || title.toLowerCase().contains("error") ||
                title.toLowerCase().contains("not found")) {
            pageType = PageType.ERROR;
        } else if (components.size() < 5 && linkedUrls.size() > 5) {
            pageType = PageType.LANDING;
        } else if (components.size() > 5 && linkedUrls.size() < 3) {
            pageType = PageType.DETAIL;
        } else {
            pageType = PageType.UNKNOWN;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Page page = (Page) o;
        return Objects.equals(url, page.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url);
    }
}
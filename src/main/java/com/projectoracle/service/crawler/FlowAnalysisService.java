package com.projectoracle.service.crawler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.projectoracle.model.Page;
import com.projectoracle.model.UIComponent;
import com.projectoracle.model.UserFlow;
import com.projectoracle.repository.ElementRepository;
import com.projectoracle.repository.UserFlowRepository;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for analyzing user flows between pages based on crawl results.
 * Identifies potential user journeys and actions for end-to-end test generation.
 * Part of the Application Analysis System described in the roadmap.
 */
@Service
public class FlowAnalysisService {

    private static final Logger logger = LoggerFactory.getLogger(FlowAnalysisService.class);

    @Autowired
    private ElementRepository elementRepository;
    
    @Autowired
    private UserFlowRepository userFlowRepository;

    /**
     * Analyzes the collection of pages to identify user flows
     *
     * @param pages the collection of pages from the crawl
     * @return map of source page to potential destination pages with action components
     */
    public Map<Page, List<PageTransition>> analyzeUserFlows(List<Page> pages) {
        Map<Page, List<PageTransition>> flowMap = new HashMap<>();

        logger.info("Analyzing user flows across {} pages", pages.size());

        // Create URL to Page mapping for quick lookup
        Map<String, Page> urlToPageMap = pages.stream()
                                              .collect(Collectors.toMap(Page::getUrl, page -> page));

        // For each page, find potential transitions
        for (Page sourcePage : pages) {
            List<PageTransition> transitions = new ArrayList<>();

            // Find interactive components that might lead to other pages
            List<UIComponent> interactiveComponents = sourcePage.getComponents().stream()
                                                                .filter(UIComponent::isInteractive)
                                                                .collect(Collectors.toList());

            // For each component, determine if it might lead to another page
            for (UIComponent component : interactiveComponents) {
                // Check if this component is a link (direct navigation)
                if ("link".equals(component.getType()) || (component.getType() != null &&
                        component.getType().equals("interactive") &&
                        sourcePage.getLinkedUrls() != null && !sourcePage.getLinkedUrls().isEmpty())) {

                    // Try to find a matching destination page for links
                    for (String linkedUrl : sourcePage.getLinkedUrls()) {
                        Page destPage = urlToPageMap.get(linkedUrl);
                        if (destPage != null) {
                            // Create a transition
                            PageTransition transition = new PageTransition(
                                    sourcePage,
                                    destPage,
                                    component,
                                    TransitionType.LINK_NAVIGATION);
                            transitions.add(transition);
                        }
                    }
                }
                // Check if this is a form submit button
                else if (("button".equals(component.getType()) ||
                        ("input".equals(component.getType()) &&
                                ("submit".equals(component.getSubtype()) || "button".equals(component.getSubtype())))) &&
                        component.getFormId() != null) {

                    // For forms, we don't always know the destination, but can infer based on page types
                    Page destPage = findPotentialFormDestination(sourcePage, pages);
                    if (destPage != null) {
                        // Create a form submission transition
                        PageTransition transition = new PageTransition(
                                sourcePage,
                                destPage,
                                component,
                                TransitionType.FORM_SUBMISSION);
                        transitions.add(transition);
                    }
                }
            }

            // Add all transitions for this page
            if (!transitions.isEmpty()) {
                flowMap.put(sourcePage, transitions);
            }
        }

        logger.info("Identified user flows connecting {} pages", flowMap.size());
        return flowMap;
    }

    /**
     * Find likely destination page for a form submission
     * Uses heuristics based on page types and URLs
     */
    private Page findPotentialFormDestination(Page sourcePage, List<Page> allPages) {
        if (sourcePage.getPageType() == Page.PageType.LOGIN) {
            // Login form likely leads to dashboard
            return allPages.stream()
                           .filter(p -> p.getPageType() == Page.PageType.DASHBOARD)
                           .findFirst()
                           .orElse(null);
        } else if (sourcePage.getPageType() == Page.PageType.FORM) {
            // Regular forms might lead to confirmation/detail pages
            // Try to find pages with similar URL patterns
            String sourceUrl = sourcePage.getUrl();

            // Check for "create" forms leading to "view" or "detail" pages
            if (sourceUrl.contains("create") || sourceUrl.contains("new") || sourceUrl.contains("add")) {
                return allPages.stream()
                               .filter(p -> p.getPageType() == Page.PageType.DETAIL ||
                                       (p.getUrl().contains("view") || p.getUrl().contains("detail")))
                               .findFirst()
                               .orElse(null);
            }

            // Check for edit forms
            if (sourceUrl.contains("edit") || sourceUrl.contains("update")) {
                return allPages.stream()
                               .filter(p -> p.getPageType() == Page.PageType.DETAIL &&
                                       (p.getUrl().contains(sourceUrl.replaceAll("edit|update", ""))))
                               .findFirst()
                               .orElse(null);
            }

            // Otherwise, look for any page with a "success" or "confirmation" in URL/title
            return allPages.stream()
                           .filter(p -> p.getUrl().contains("success") ||
                                   p.getUrl().contains("confirmation") ||
                                   p.getTitle().toLowerCase().contains("success") ||
                                   p.getTitle().toLowerCase().contains("confirmation"))
                           .findFirst()
                           .orElse(null);
        }

        // Default: No clear destination found
        return null;
    }

    /**
     * Identifies likely user journeys across pages
     *
     * @param pages the collection of pages from the crawl
     * @return list of multi-page user journeys
     */
    public List<UserJourney> identifyUserJourneys(List<Page> pages) {
        List<UserJourney> journeys = new ArrayList<>();
        Map<Page, List<PageTransition>> flowMap = analyzeUserFlows(pages);

        // Start with key flows from common entry points
        Page loginPage = pages.stream()
                              .filter(p -> p.getPageType() == Page.PageType.LOGIN)
                              .findFirst()
                              .orElse(null);

        Page landingPage = pages.stream()
                                .filter(p -> p.getPageType() == Page.PageType.LANDING)
                                .findFirst()
                                .orElse(null);

        // Build user journey starting from login page (if exists)
        if (loginPage != null) {
            UserJourney loginJourney = buildJourneyFromPage(loginPage, flowMap, new HashSet<>(), 3);
            if (loginJourney != null && loginJourney.getTransitions().size() > 1) {
                loginJourney.setName("Login to Dashboard Flow");
                loginJourney.setDescription("User logs in and navigates through dashboard");
                journeys.add(loginJourney);
            }
        }

        // Build user journey starting from landing page (if exists)
        if (landingPage != null) {
            UserJourney landingJourney = buildJourneyFromPage(landingPage, flowMap, new HashSet<>(), 3);
            if (landingJourney != null && landingJourney.getTransitions().size() > 1) {
                landingJourney.setName("Main Navigation Flow");
                landingJourney.setDescription("User navigates from landing page through main sections");
                journeys.add(landingJourney);
            }
        }

        // Find CRUD flows
        // Create -> Read flow
        Page createFormPage = pages.stream()
                                   .filter(p -> p.getPageType() == Page.PageType.FORM &&
                                           (p.getUrl().contains("create") || p.getUrl().contains("new") || p.getUrl().contains("add")))
                                   .findFirst()
                                   .orElse(null);

        if (createFormPage != null) {
            UserJourney crudJourney = buildJourneyFromPage(createFormPage, flowMap, new HashSet<>(), 2);
            if (crudJourney != null && crudJourney.getTransitions().size() > 0) {
                crudJourney.setName("Create and View Flow");
                crudJourney.setDescription("User creates a new item and views details");
                journeys.add(crudJourney);
            }
        }

        // List -> Detail flow
        Page listingPage = pages.stream()
                                .filter(p -> p.getPageType() == Page.PageType.LISTING)
                                .findFirst()
                                .orElse(null);

        if (listingPage != null) {
            UserJourney listDetailJourney = buildJourneyFromPage(listingPage, flowMap, new HashSet<>(), 2);
            if (listDetailJourney != null && listDetailJourney.getTransitions().size() > 0) {
                listDetailJourney.setName("List to Detail Flow");
                listDetailJourney.setDescription("User browses a list and views item details");
                journeys.add(listDetailJourney);
            }
        }

        logger.info("Identified {} potential user journeys", journeys.size());
        return journeys;
    }
    
    /**
     * Find the most common user journeys in the application
     * 
     * @param minFlowsInJourney Minimum number of flows in a journey to be included
     * @param maxJourneys Maximum number of journeys to return
     * @return Map of journey name to list of flows in the journey
     */
    public Map<String, List<UserFlow>> findCommonUserJourneys(int minFlowsInJourney, int maxJourneys) {
        logger.info("Finding common user journeys with min flows: {}, max journeys: {}", minFlowsInJourney, maxJourneys);
        
        // Get flows from repository
        List<UserFlow> allFlows = userFlowRepository.getAllFlows();
        
        // Map to store journeys
        Map<String, List<UserFlow>> journeys = new HashMap<>();
        
        if (allFlows.isEmpty()) {
            logger.warn("No flows found in repository");
            return journeys;
        }
        
        // Group flows by source page
        Map<UUID, List<UserFlow>> flowsBySource = new HashMap<>();
        
        for (UserFlow flow : allFlows) {
            flowsBySource.computeIfAbsent(flow.getSourcePageId(), k -> new ArrayList<>()).add(flow);
        }
        
        // Find entry points (pages with no incoming flows)
        Set<UUID> entryPoints = new HashSet<>(flowsBySource.keySet());
        for (UserFlow flow : allFlows) {
            entryPoints.remove(flow.getTargetPageId());
        }
        
        // If no entry points found, use pages with the most outgoing flows
        if (entryPoints.isEmpty()) {
            logger.info("No clear entry points found, using most connected pages");
            flowsBySource.entrySet().stream()
                .sorted((e1, e2) -> Integer.compare(e2.getValue().size(), e1.getValue().size()))
                .limit(3)
                .forEach(e -> entryPoints.add(e.getKey()));
        }
        
        // For each entry point, find all possible paths
        List<List<UserFlow>> allPaths = new ArrayList<>();
        for (UUID entryPoint : entryPoints) {
            findAllPaths(entryPoint, new ArrayList<>(), allFlows, allPaths, 0, 5);
        }
        
        // Filter paths to include only those meeting minimum length
        allPaths = allPaths.stream()
            .filter(path -> path.size() >= minFlowsInJourney)
            .sorted((p1, p2) -> Integer.compare(p2.size(), p1.size()))
            .collect(Collectors.toList());
        
        // Take top N journeys
        int journeyCount = 0;
        for (List<UserFlow> path : allPaths) {
            if (journeyCount >= maxJourneys) {
                break;
            }
            
            // Check if this journey is unique enough compared to ones we already have
            if (isDistinctJourney(path, journeys.values())) {
                String journeyName = generateJourneyName(path, journeyCount + 1);
                journeys.put(journeyName, path);
                journeyCount++;
            }
        }
        
        logger.info("Found {} common user journeys", journeys.size());
        return journeys;
    }
    
    /**
     * Check if a journey is distinct from existing journeys
     */
    private boolean isDistinctJourney(List<com.projectoracle.model.UserFlow> newJourney, 
                                    Collection<List<com.projectoracle.model.UserFlow>> existingJourneys) {
        for (List<com.projectoracle.model.UserFlow> existing : existingJourneys) {
            // If journeys share more than 70% of the same flows, consider them duplicates
            long commonFlows = newJourney.stream()
                    .filter(flow -> existing.contains(flow))
                    .count();
            
            double similarity = (double) commonFlows / Math.min(newJourney.size(), existing.size());
            if (similarity > 0.7) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Generate a descriptive name for a journey
     */
    private String generateJourneyName(List<com.projectoracle.model.UserFlow> journey, int index) {
        if (journey == null || journey.isEmpty()) {
            return "Journey " + index;
        }
        
        // Get first and last flow
        com.projectoracle.model.UserFlow firstFlow = journey.get(0);
        com.projectoracle.model.UserFlow lastFlow = journey.get(journey.size() - 1);
        
        // Get page titles if available
        String startTitle = firstFlow.getSourcePage() != null ? firstFlow.getSourcePage().getTitle() : "Start";
        String endTitle = lastFlow.getTargetPage() != null ? lastFlow.getTargetPage().getTitle() : "End";
        
        // Check for common journey types
        boolean hasFormSubmission = journey.stream().anyMatch(com.projectoracle.model.UserFlow::isFormSubmission);
        boolean hasLogin = journey.stream()
                .anyMatch(flow -> flow.getInteractionDescription() != null && 
                        flow.getInteractionDescription().toLowerCase().contains("login"));
                        
        // Create name based on journey characteristics
        StringBuilder name = new StringBuilder();
        
        if (hasLogin) {
            name.append("Login to ").append(endTitle);
        } else if (hasFormSubmission) {
            name.append("Form Submission: ").append(startTitle).append(" → ").append(endTitle);
        } else {
            name.append("Journey ").append(index).append(": ").append(startTitle).append(" → ").append(endTitle);
        }
        
        return name.toString();
    }
    
    /**
     * Recursively find all paths from a starting page
     */
    private void findAllPaths(UUID currentPageId, List<com.projectoracle.model.UserFlow> currentPath, 
                            List<com.projectoracle.model.UserFlow> allFlows, 
                            List<List<com.projectoracle.model.UserFlow>> results, 
                            int depth, int maxDepth) {
        // Check max depth
        if (depth >= maxDepth) {
            if (!currentPath.isEmpty()) {
                results.add(new ArrayList<>(currentPath));
            }
            return;
        }
        
        // Find all flows from current page
        List<com.projectoracle.model.UserFlow> outgoingFlows = allFlows.stream()
                .filter(flow -> flow.getSourcePageId().equals(currentPageId))
                .collect(Collectors.toList());
        
        // If no outgoing flows, add current path to results
        if (outgoingFlows.isEmpty()) {
            if (!currentPath.isEmpty()) {
                results.add(new ArrayList<>(currentPath));
            }
            return;
        }
        
        // For each outgoing flow, continue path
        for (com.projectoracle.model.UserFlow flow : outgoingFlows) {
            // Check for cycles
            boolean alreadyInPath = currentPath.stream()
                    .anyMatch(f -> f.getId().equals(flow.getId()));
            
            if (!alreadyInPath) {
                // Add flow to current path
                currentPath.add(flow);
                
                // Continue from target page
                findAllPaths(flow.getTargetPageId(), currentPath, allFlows, results, depth + 1, maxDepth);
                
                // Backtrack
                currentPath.remove(currentPath.size() - 1);
            }
        }
    }
    
    /**
     * Convert a user flow to a test case structure
     * 
     * @param flow The flow to convert
     * @param testPrefix Prefix for test name and class
     * @return A TestCase object
     */
    public com.projectoracle.model.TestCase convertFlowToTestCase(UserFlow flow, String testPrefix) {
        // Generate test name
        String testName = testPrefix + "_";
        
        if (flow.getSourcePage() != null && flow.getTargetPage() != null) {
            testName += sanitizeForMethodName(flow.getSourcePage().getTitle()) + "_to_" + 
                    sanitizeForMethodName(flow.getTargetPage().getTitle());
        } else {
            testName += "Flow_" + flow.getId().toString().substring(0, 8);
        }
        
        // Create test case
        com.projectoracle.model.TestCase testCase = new com.projectoracle.model.TestCase();
        testCase.setId(UUID.randomUUID());
        testCase.setName(testName);
        
        // Set description based on flow type
        StringBuilder description = new StringBuilder();
        description.append("Test ");
        
        if (flow.isFormSubmission()) {
            description.append("form submission");
        } else if ("navigation".equals(flow.getFlowType())) {
            description.append("navigation");
        } else if ("state_change".equals(flow.getFlowType())) {
            description.append("state change");
        } else {
            description.append("user flow");
        }
        
        if (flow.getSourcePage() != null && flow.getTargetPage() != null) {
            description.append(" from ")
                      .append(flow.getSourcePage().getTitle())
                      .append(" to ")
                      .append(flow.getTargetPage().getTitle());
        }
        
        testCase.setDescription(description.toString());
        
        // Set other properties
        testCase.setType(com.projectoracle.model.TestCase.TestType.UI);
        testCase.setPriority(convertPriorityScore(flow.getPriorityScore()));
        testCase.setStatus(com.projectoracle.model.TestCase.TestStatus.GENERATED);
        testCase.setMethodName("test" + sanitizeForMethodName(testName));
        testCase.setClassName(testPrefix + "Test");
        testCase.setPackageName("com.projectoracle.test.ui.flow");
        
        return testCase;
    }
    
    /**
     * Convert priority score to TestPriority enum
     */
    private com.projectoracle.model.TestCase.TestPriority convertPriorityScore(int score) {
        if (score >= 80) {
            return com.projectoracle.model.TestCase.TestPriority.CRITICAL;
        } else if (score >= 60) {
            return com.projectoracle.model.TestCase.TestPriority.HIGH;
        } else if (score >= 40) {
            return com.projectoracle.model.TestCase.TestPriority.MEDIUM;
        } else {
            return com.projectoracle.model.TestCase.TestPriority.LOW;
        }
    }
    
    /**
     * Sanitize a string for use in a Java method name
     */
    private String sanitizeForMethodName(String input) {
        if (input == null || input.isEmpty()) {
            return "Unknown";
        }
        
        // Replace non-alphanumeric characters with underscores
        String sanitized = input.replaceAll("[^a-zA-Z0-9]", "_");
        
        // Ensure it starts with a letter
        if (!Character.isLetter(sanitized.charAt(0))) {
            sanitized = "Page_" + sanitized;
        }
        
        // Limit length
        if (sanitized.length() > 30) {
            sanitized = sanitized.substring(0, 30);
        }
        
        return sanitized;
    }

    /**
     * Recursively builds a journey starting from a specific page
     */
    private UserJourney buildJourneyFromPage(Page startPage,
            Map<Page, List<PageTransition>> flowMap,
            Set<String> visitedUrls,
            int maxDepth) {
        if (maxDepth <= 0 || visitedUrls.contains(startPage.getUrl())) {
            return null;
        }

        // Mark this page as visited
        visitedUrls.add(startPage.getUrl());

        UserJourney journey = new UserJourney();
        List<PageTransition> transitions = flowMap.getOrDefault(startPage, new ArrayList<>());

        // If no outgoing transitions, return null
        if (transitions.isEmpty()) {
            return null;
        }

        // Add the first transition to this journey
        PageTransition firstTransition = transitions.get(0);
        journey.addTransition(firstTransition);

        // Recursively follow next page
        UserJourney nextJourney = buildJourneyFromPage(
                firstTransition.getDestinationPage(),
                flowMap,
                visitedUrls,
                maxDepth - 1);

        // If there's a continuing journey, add its transitions
        if (nextJourney != null) {
            for (PageTransition nextTransition : nextJourney.getTransitions()) {
                journey.addTransition(nextTransition);
            }
        }

        return journey;
    }

    /**
     * Enum representing different types of page transitions
     */
    public enum TransitionType {
        LINK_NAVIGATION,
        FORM_SUBMISSION,
        BUTTON_CLICK,
        API_REDIRECT
    }

    /**
     * Class representing a transition from one page to another
     */
    public static class PageTransition {
        private Page sourcePage;
        private Page destinationPage;
        private UIComponent actionComponent;
        private TransitionType type;

        public PageTransition(Page sourcePage, Page destinationPage,
                UIComponent actionComponent, TransitionType type) {
            this.sourcePage = sourcePage;
            this.destinationPage = destinationPage;
            this.actionComponent = actionComponent;
            this.type = type;
        }

        public Page getSourcePage() {
            return sourcePage;
        }

        public void setSourcePage(Page sourcePage) {
            this.sourcePage = sourcePage;
        }

        public Page getDestinationPage() {
            return destinationPage;
        }

        public void setDestinationPage(Page destinationPage) {
            this.destinationPage = destinationPage;
        }

        public UIComponent getActionComponent() {
            return actionComponent;
        }

        public void setActionComponent(UIComponent actionComponent) {
            this.actionComponent = actionComponent;
        }

        public TransitionType getType() {
            return type;
        }

        public void setType(TransitionType type) {
            this.type = type;
        }
    }

    /**
     * Class representing a user journey across multiple pages
     */
    public static class UserJourney {
        private String name;
        private String description;
        private List<PageTransition> transitions = new ArrayList<>();

        public void addTransition(PageTransition transition) {
            this.transitions.add(transition);
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public List<PageTransition> getTransitions() {
            return transitions;
        }

        public void setTransitions(List<PageTransition> transitions) {
            this.transitions = transitions;
        }
    }
}
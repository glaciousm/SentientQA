package com.projectoracle.rest;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

/**
 * Controller for handling login requests.
 */
@Controller
@RequestMapping("/")
public class LoginController {

    /**
     * Handles requests to /login to serve the login page.
     * 
     * @return ModelAndView for the login page
     */
    @GetMapping("/login")
    public ModelAndView login() {
        return new ModelAndView("login");
    }
    
    /**
     * Handles requests to /login.html to serve the login page.
     * 
     * @return ModelAndView for the login page
     */
    @GetMapping("/login.html")
    public ModelAndView loginHtml() {
        return new ModelAndView("login");
    }
}
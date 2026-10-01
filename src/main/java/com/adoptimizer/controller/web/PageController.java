package com.adoptimizer.controller.web;

import com.adoptimizer.config.AppProperties;
import com.adoptimizer.security.AppUserPrincipal;
import com.adoptimizer.service.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the Thymeleaf pages. Each page receives the CSRF token (rendered into a meta tag for JavaScript)
 * and, when signed in, the current user for the sidebar.
 */
@Controller
@RequiredArgsConstructor
public class PageController {

    private final ProfileService profileService;
    private final AppProperties properties;

    @GetMapping("/")
    public String index(@AuthenticationPrincipal AppUserPrincipal principal, CsrfToken csrfToken, Model model) {
        if (principal != null) {
            return "redirect:" + home(principal);
        }
        csrfToken.getToken(); // create the token (and session) before the response starts streaming
        model.addAttribute("demoMode", properties.getSeed().isEnabled());
        return "index";
    }

    @GetMapping("/admin/login")
    public String adminLogin(@AuthenticationPrincipal AppUserPrincipal principal, CsrfToken csrfToken, Model model) {
        if (principal != null) {
            return "redirect:" + home(principal);
        }
        csrfToken.getToken();
        model.addAttribute("demoMode", properties.getSeed().isEnabled());
        if (properties.getSeed().isEnabled()) {
            model.addAttribute("demoGroupCode", properties.getAdmin().getGroupCode());
        }
        return "admin-login";
    }

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal AppUserPrincipal principal, Model model, CsrfToken csrfToken) {
        csrfToken.getToken();
        model.addAttribute("currentUser", profileService.getUser(principal.getId()));
        return "dashboard";
    }

    @GetMapping("/admin")
    public String admin(@AuthenticationPrincipal AppUserPrincipal principal, Model model, CsrfToken csrfToken) {
        csrfToken.getToken();
        model.addAttribute("currentUser", profileService.getUser(principal.getId()));
        return "admin";
    }

    /** The original static prototype used .html file names; keep old bookmarks working. */
    @GetMapping({"/index.html", "/Index.html"})
    public String legacyIndex() {
        return "redirect:/";
    }

    @GetMapping("/admin-login.html")
    public String legacyAdminLogin() {
        return "redirect:/admin/login";
    }

    @GetMapping("/dashboard.html")
    public String legacyDashboard() {
        return "redirect:/dashboard";
    }

    @GetMapping({"/admin.html", "/Admin.html"})
    public String legacyAdmin() {
        return "redirect:/admin";
    }

    private static String home(AppUserPrincipal principal) {
        return principal.isAdmin() ? "/admin" : "/dashboard";
    }
}

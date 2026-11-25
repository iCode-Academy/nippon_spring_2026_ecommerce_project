package mn.icode.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.util.StreamUtils;

/**
 * Guards accessibility and responsive-layout requirements for the shared UI:
 * visible keyboard focus, viewport meta tag, and mobile breakpoints.
 */
class UiAssetsTest {

    private static String resource(String path) throws IOException {
        try (InputStream in = UiAssetsTest.class.getResourceAsStream(path)) {
            assertThat(in).as("resource %s exists", path).isNotNull();
            return StreamUtils.copyToString(in, StandardCharsets.UTF_8);
        }
    }

    @Test
    void stylesheetDefinesVisibleKeyboardFocus() throws Exception {
        String css = resource("/static/css/app.css");

        assertThat(css).contains(":focus-visible");
        assertThat(css).contains("outline: 3px solid");
    }

    @Test
    void stylesheetContainsMobileBreakpointAndOverflowGuard() throws Exception {
        String css = resource("/static/css/app.css");

        assertThat(css).contains("@media (max-width: 40rem)");
        assertThat(css).contains("overflow-x: auto");
    }

    @Test
    void layoutDeclaresResponsiveViewport() throws Exception {
        String layout = resource("/templates/fragments/layout.html");

        assertThat(layout)
                .contains("name=\"viewport\"")
                .contains("width=device-width");
    }

    @Test
    void allPagesUseTheSharedLayoutHead() throws Exception {
        String[] pages = {
                "/templates/index.html",
                "/templates/login.html",
                "/templates/register.html",
                "/templates/home.html",
                "/templates/orders.html",
                "/templates/products/detail.html",
                "/templates/products/list.html",
                "/templates/admin/dashboard.html",
                "/templates/admin/categories.html",
                "/templates/admin/products.html",
                "/templates/error/403.html",
                "/templates/error/404.html",
                "/templates/error/500.html"
        };

        for (String page : pages) {
            String html = resource(page);
            assertThat(html)
                    .as("page %s uses the shared layout head", page)
                    .contains("fragments/layout :: head")
                    .contains("fragments/layout :: nav")
                    .contains("id=\"main\"");
        }
    }

    @Test
    void adminPagesUseTheSharedSidebarShell() throws Exception {
        for (String page : new String[] {
                "/templates/admin/dashboard.html",
                "/templates/admin/categories.html",
                "/templates/admin/products.html"}) {
            assertThat(resource(page))
                    .as("page %s uses the admin sidebar shell", page)
                    .contains("class=\"admin-shell\"")
                    .contains("fragments/admin-sidebar :: adminSidebar")
                    .contains("class=\"admin-main\"");
        }
    }

    @Test
    void errorTemplatesAreNotBlank() throws Exception {
        for (String page : new String[] {
                "/templates/error/403.html",
                "/templates/error/404.html",
                "/templates/error/500.html"}) {
            assertThat(resource(page).trim())
                    .as("error page %s has content", page)
                    .isNotEmpty();
        }
    }

    @Test
    void layoutExposesCsrfMetaTagsForJavaScriptClients() throws Exception {
        String layout = resource("/templates/fragments/layout.html");

        assertThat(layout)
                .contains("name=\"_csrf\"")
                .contains("name=\"_csrf_header\"")
                .doesNotContain("js/app.js");
    }

    @Test
    void adminCategoriesJsReadsCsrfMetaTagsAndCallsTheApi() throws Exception {
        String js = resource("/static/js/admin-categories.js");

        assertThat(js)
                .contains("meta[name=\"_csrf\"]")
                .contains("meta[name=\"_csrf_header\"]")
                .contains("[csrfHeader]: csrfToken")
                .contains("fetch(API_URL)");
    }

    @Test
    void categoriesPageLoadsTheAdminCategoriesScript() throws Exception {
        String page = resource("/templates/admin/categories.html");

        assertThat(page)
                .contains("js/admin-categories.js")
                .contains("id=\"categoryForm\"")
                .contains("id=\"categoriesTableBody\"")
                .contains("id=\"categoriesTableWrap\"")
                .contains("id=\"message\"")
                .contains("id=\"emptyTable\"")
                .doesNotContain("onclick=");
    }

    @Test
    void adminCategoriesJsUsesSharedFlashStyling() throws Exception {
        String js = resource("/static/js/admin-categories.js");

        assertThat(js)
                .contains("flash-success")
                .contains("flash-error")
                .contains("#categoriesTableWrap")
                .doesNotContain("style.position")
                .doesNotContain("style.background");
    }

    @Test
    void detailPageDefinesResponsiveGalleryAndEmptyStates() throws Exception {
        String page = resource("/templates/products/detail.html");
        String css = resource("/static/css/app.css");

        assertThat(page)
                .contains("class=\"product-detail-grid\"")
                .contains("class=\"gallery-thumbs\"")
                .contains("No images available")
                .contains("No published reviews yet");
        assertThat(css)
                .contains(".product-detail-grid")
                .contains(".gallery-thumb")
                .contains("@media (max-width: 40rem)");
    }

    @Test
    void layoutAndAdminSidebarSplitCustomerAndAdminNavigation() throws Exception {
        String layout = resource("/templates/fragments/layout.html");
        String sidebar = resource("/templates/fragments/admin-sidebar.html");
        String css = resource("/static/css/app.css");

        assertThat(layout)
                .contains("@{/products}")
                .contains("@{/admin}")
                .doesNotContain("@{/admin/products}")
                .contains("nav-active");
        assertThat(sidebar)
                .contains("@{/admin}")
                .contains("@{/admin/products}")
                .contains("@{/admin/categories}")
                .contains("is-active");
        assertThat(css)
                .contains(".nav-active")
                .contains(".admin-nav-link.is-active")
                .contains(".admin-shell");
    }

    @Test
    void adminSidebarUsesMobileHorizontalNavigation() throws Exception {
        String css = resource("/static/css/app.css");

        assertThat(css)
                .contains("@media (max-width: 60rem)")
                .contains("overflow-x: auto")
                .contains(".admin-sidebar");
    }

    @Test
    void adminProductsScriptUsesWorkingProductActionEndpoints() throws Exception {
        String js = resource("/static/js/admin-products.js");

        assertThat(js)
                .contains("/toggle-active")
                .contains("/adjust-stock")
                .contains("stock-adjust")
                .doesNotContain("updatedAt")
                .doesNotContain("prompt(");
    }

    @Test
    void productCardFragmentProvidesImageAndDetailAction() throws Exception {
        String fragment = resource("/templates/fragments/product-card.html");
        String css = resource("/static/css/app.css");

        assertThat(fragment)
                .contains("class=\"card-media\"")
                .contains("No image available")
                .contains("View details")
                .contains("@{/products/{id}(id=${product.id})}");
        assertThat(css)
                .contains(".card-media")
                .contains(".card-actions")
                .contains("--shadow-md");
    }

    @Test
    void catalogPageIncludesFilterSummaryAndNumberedPagination() throws Exception {
        String page = resource("/templates/products/list.html");

        assertThat(page)
                .contains("class=\"filter-summary\"")
                .contains("Clear all")
                .contains("class=\"chip-list\"")
                .contains("pageNumbers")
                .contains("aria-current=\"page\"");
    }

    @Test
    void adminProductsPageIncludesToolbarAndSuccessFlash() throws Exception {
        String page = resource("/templates/admin/products.html");

        assertThat(page)
                .contains("class=\"breadcrumb\"")
                .contains("Add product")
                .contains("id=\"products-flash\"");
    }
}

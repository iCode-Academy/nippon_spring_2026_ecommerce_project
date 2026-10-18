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
                "/templates/admin/dashboard.html",
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
}

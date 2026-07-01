package dev.example.agent;

import com.google.inject.Inject;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class CodingTools {
    public static final String TOOL_SET_ID = "coding-tools-v1";

    private static final List<Pattern> BLOCKED_COMMANDS = List.of(
            Pattern.compile("\\bsudo\\b"), Pattern.compile("\\bsu\\b"), Pattern.compile("\\bssh\\b"),
            Pattern.compile("\\bscp\\b"), Pattern.compile("\\brsync\\b"), Pattern.compile("\\bdocker\\b"),
            Pattern.compile("\\bkubectl\\b"), Pattern.compile("\\bmount\\b"), Pattern.compile("\\bumount\\b"),
            Pattern.compile("\\biptables\\b"), Pattern.compile("\\bshutdown\\b"), Pattern.compile("\\breboot\\b"),
            Pattern.compile("\\bmkfs\\b"), Pattern.compile("\\bdd\\b"), Pattern.compile("rm\\s+-rf\\s+/")
    );
    private final AppConfig config;
    private final HttpClient httpClient;

    @Inject
    public CodingTools(AppConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(10)).build();
        config.workspaceDir().toFile().mkdirs();
    }

    @Tool(
            name = "execute_bash",
            value = """
                Execute a bash command inside the isolated /workspace directory.

                Use this for coding tasks:
                - inspect files and directories
                - create or edit files
                - run tests
                - run grep/ripgrep/find
                - inspect command output and logs

                Do not use this for:
                - long-running servers
                - SSH/SCP/rsync
                - Docker or Kubernetes commands
                - destructive host operations
                - reading secrets
                """
    )
    public String execute_bash(
            @P(
                    name = "command",
                    description = """
                        Bash command to run inside /workspace.

                        Prefer small, verifiable commands.
                        The command runs with /workspace as cwd.
                        The command is subject to timeout and safety filtering.
                        """
            )
            String command
    ) {
        if (command == null || command.isBlank()) return "No command provided.";
        if (isDangerous(command)) return "Blocked: command looked dangerous for this learning sandbox. Try a safer command limited to /workspace.";
        ProcessBuilder builder = new ProcessBuilder("/bin/bash", "-lc", command);
        builder.directory(config.workspaceDir().toFile());
        builder.environment().clear();
        builder.environment().putAll(Map.of("PATH", "/usr/local/bin:/usr/bin:/bin", "HOME", config.workspaceDir().toString(), "LANG", "C.UTF-8"));
        try {
            Process process = builder.start();
            boolean finished = process.waitFor(config.bashTimeout().toSeconds(), TimeUnit.SECONDS);
            if (!finished) { process.destroyForcibly(); return "Command timed out after " + config.bashTimeout().toSeconds() + "s."; }
            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            return truncate("STDOUT:\n" + stdout + "\n\nSTDERR:\n" + stderr + "\n\nEXIT_CODE: " + process.exitValue(), config.maxToolOutputChars());
        } catch (Exception e) { return "Command failed: " + e.getClass().getSimpleName() + ": " + safeMessage(e); }
    }

    @Tool(
            name = "web_search",
            value = """
                Search the public web for current technical information.

                Use this when:
                - library APIs may have changed
                - error messages need investigation
                - current documentation is needed
                - public facts may be stale

                Return value contains result titles, URLs, and snippets.
                Prefer fetch_url afterwards when one specific result is relevant.
                """
    )
    public String web_search(
            @P(
                    name = "query",
                    description = "Search query. Include library/framework names, error messages, and version numbers when available."
            )
            String query,

            @P(
                    name = "maxResults",
                    description = "Maximum number of results to return. Usually 3 to 5 is enough.",
                    required = false
            )
            Integer maxResults
    ) {
        if (query == null || query.isBlank()) return "No query provided.";
        int limit = Math.max(1, Math.min(maxResults, 10));
        URI uri = URI.create("https://duckduckgo.com/html/?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8));
        try {
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15)).header("User-Agent", "lc4j-coding-agent/0.1").GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            Document doc = Jsoup.parse(response.body());
            StringBuilder out = new StringBuilder();
            int count = 0;
            for (Element result : doc.select(".result")) {
                if (count >= limit) break;
                Element title = result.selectFirst(".result__a");
                Element snippet = result.selectFirst(".result__snippet");
                if (title == null) continue;
                count++;
                String href = title.absUrl("href").isBlank() ? title.attr("href") : title.absUrl("href");
                out.append(count).append(". ").append(title.text()).append("\nURL: ").append(href).append("\nSnippet: ").append(snippet == null ? "" : snippet.text()).append("\n\n");
            }
            return count == 0 ? "No search results found." : truncate(out.toString(), config.maxToolOutputChars());
        } catch (Exception e) { return "Web search failed: " + e.getClass().getSimpleName() + ": " + safeMessage(e); }
    }

    @Tool(
            name = "fetch_url",
            value = """
                Fetch an HTTP or HTTPS URL and extract readable page text.

                Use after web_search when:
                - a specific result looks relevant
                - documentation details are needed
                - the final answer should cite or summarize a page

                This tool only handles HTML or plain text.
                """
    )
    public String fetch_url(
            @P(
                    name = "url",
                    description = "HTTP or HTTPS URL to fetch."
            )
            String url
    ) {
        if (url == null || url.isBlank()) return "No URL provided.";
        URI uri;
        try { uri = URI.create(url); } catch (IllegalArgumentException e) { return "Invalid URL: " + url; }
        if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) return "Only http and https URLs are allowed.";
        try {
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20)).header("User-Agent", "lc4j-coding-agent/0.1").GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String contentType = response.headers().firstValue("content-type").orElse("");
            if (!contentType.contains("text/html") && !contentType.contains("text/plain")) return "Fetched URL, but content-type was '" + contentType + "'. This tool only extracts HTML/plain text.";
            Document doc = Jsoup.parse(response.body());
            doc.select("script, style, noscript").remove();
            return truncate(doc.text(), 12_000);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return "Fetch failed: " + e.getClass().getSimpleName() + ": " + safeMessage(e);
        }
    }

    private boolean isDangerous(String command) { return BLOCKED_COMMANDS.stream().anyMatch(pattern -> pattern.matcher(command).find()); }
    private static String truncate(String text, int maxChars) { return text == null ? "" : text.length() <= maxChars ? text : text.substring(0, maxChars) + "\n\n...[truncated to " + maxChars + " chars]"; }
    private static String safeMessage(Exception e) { return e.getMessage() == null ? "" : e.getMessage(); }
}

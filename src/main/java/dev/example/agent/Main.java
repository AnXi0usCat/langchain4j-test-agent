package dev.example.agent;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.zaxxer.hikari.HikariDataSource;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.jboss.resteasy.plugins.server.servlet.HttpServletDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);
    static Injector injector;

    public static void main(String[] args) throws Exception {
        injector = Guice.createInjector(new AppModule());
        AppConfig config = injector.getInstance(AppConfig.class);
        injector.getInstance(AgentRepository.class).migrate();

        Server server = new Server(config.port());
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");

        ServletHolder restEasy = new ServletHolder(new HttpServletDispatcher());
        restEasy.setInitParameter("jakarta.ws.rs.Application", AgentApplication.class.getName());
        context.addServlet(restEasy, "/*");
        server.setHandler(context);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { server.stop(); } catch (Exception e) { log.warn("Error while stopping Jetty", e); }
            try { injector.getInstance(HikariDataSource.class).close(); } catch (Exception e) { log.warn("Error while closing datasource", e); }
        }));

        log.info("Starting Jetty on port {}", config.port());
        server.start();
        server.join();
    }
}

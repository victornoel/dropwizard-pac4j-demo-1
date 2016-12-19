package com.uberlogik.demo.security;

import com.uberlogik.pac4j.matching.UrlPathMatcher;
import io.dropwizard.Configuration;
import io.dropwizard.ConfiguredBundle;
import io.dropwizard.jetty.MutableServletContextHandler;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.eclipse.jetty.server.session.SessionHandler;
import org.pac4j.core.authorization.authorizer.RequireAnyRoleAuthorizer;
import org.pac4j.core.client.Clients;
import org.pac4j.core.config.Config;
import org.pac4j.dropwizard.DefaultFeatureSupport;
import org.pac4j.dropwizard.Pac4jFeatureSupport;
import org.pac4j.http.client.indirect.FormClient;
import org.pac4j.jax.rs.features.Pac4JSecurityFeature;
import org.pac4j.jax.rs.features.Pac4JSecurityFilterFeature;
import org.pac4j.jax.rs.jersey.features.Pac4JValueFactoryProvider;
import org.pac4j.jax.rs.pac4j.JaxRsCallbackUrlResolver;
import org.pac4j.jax.rs.servlet.features.ServletJaxRsContextFactoryProvider;
import org.pac4j.jax.rs.servlet.pac4j.ServletSessionStore;

import java.util.ArrayList;
import java.util.Collection;


public class SecurityBundle<T extends Configuration> implements ConfiguredBundle<T>
{
    public static final String SESSION_KEY = "s";
    private static final String FORM_CLIENT_CALLBACK = "/callback";
    private static final String FORM_CLIENT_NAME = "form";


    private Config config;

    public Config getConfig()
    {
        return config;
    }

    @Override
    public final void initialize(Bootstrap<?> bootstrap)
    {
        for (Pac4jFeatureSupport fs : supportedFeatures())
        {
            fs.setup(bootstrap);
        }
    }

    public Collection<Pac4jFeatureSupport> supportedFeatures()
    {
        ArrayList<Pac4jFeatureSupport> res = new ArrayList<>();
        res.add(new DefaultFeatureSupport());
        return res;
    }

    public String getFormClientURl()
    {
        return FORM_CLIENT_CALLBACK + "?client_name=" + FORM_CLIENT_NAME;
    }

    @Override
    public final void run(T configuration, Environment environment)
            throws Exception
    {
        // see build method
    }

    public void build(Environment environment, org.jooq.Configuration jooqConfig)
    {
        // Dropwizard does not enable sessions by default, so add a session handler
        MutableServletContextHandler contextHandler = environment.getApplicationContext();
        contextHandler.setSessionHandler(new SessionHandler());
        contextHandler.getServletContext().getSessionCookieConfig().setName(SESSION_KEY);

        // pac4j configuration

        config = new Config();
        config.setSessionStore(new ServletSessionStore());
        environment.jersey().register(new ServletJaxRsContextFactoryProvider(config));
        environment.jersey().register(new Pac4JSecurityFeature(config));
        environment.jersey().register(new Pac4JValueFactoryProvider.Binder());

        // require authentication everywhere, except for whitelisted paths

        final String matcherName = "authPathMatcher";
        UrlPathMatcher matcher = new UrlPathMatcher();
        matcher.addExcludedPath("/");
        matcher.addExcludedPath("/login");
        matcher.addExcludedPath(FORM_CLIENT_CALLBACK);
        config.addMatcher(matcherName, matcher);

        // TODO: can we add an authorizer that can check an arbitrary role?
        config.addAuthorizer("superuser", new RequireAnyRoleAuthorizer<>("superuser"));

        JooqAuthenticator authenticator = new JooqAuthenticator(jooqConfig);
        FormClient formClient = new FormClient("/login", authenticator);
        formClient.setName(FORM_CLIENT_NAME);
        formClient.setCallbackUrl(FORM_CLIENT_CALLBACK);

        // authorization information is present in profile, so generator is a no-op
        formClient.addAuthorizationGenerator(profile -> {});

        Clients clients = new Clients(formClient);
        clients.setCallbackUrlResolver(new JaxRsCallbackUrlResolver());
        config.setClients(clients);

        environment.jersey()
                .register(new Pac4JSecurityFilterFeature(config, null,
                        "isAuthenticated", FORM_CLIENT_NAME,
                        "authPathMatcher", null));

    }
}

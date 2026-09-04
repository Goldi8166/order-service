package com.example.messaging;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;

/**
 * Wires up the OpenSearch Java client from application.properties config
 * (opensearch.host / opensearch.port) so it can be @Inject-ed anywhere.
 *
 * The producer method itself is @ApplicationScoped (created once), but the bean
 * it produces (OpenSearchClient) is left @Dependent (no scope annotation) on
 * purpose - OpenSearchClient has no no-args constructor, so Quarkus's CDI
 * container (Arc) cannot generate a client proxy for it under a normal scope
 * like @ApplicationScoped. @Dependent beans are injected directly, no proxy
 * needed, which avoids that build error.
 */
@ApplicationScoped
public class OpenSearchClientProducer {

    @ConfigProperty(name = "opensearch.host", defaultValue = "localhost")
    String host;

    @ConfigProperty(name = "opensearch.port", defaultValue = "9200")
    int port;

    @Produces
    public OpenSearchClient openSearchClient() {
        final org.apache.hc.core5.http.HttpHost httpHost =
                new org.apache.hc.core5.http.HttpHost("http", host, port);

        OpenSearchTransport transport = ApacheHttpClient5TransportBuilder
                .builder(httpHost)
                .build();

        return new OpenSearchClient(transport);
    }
}

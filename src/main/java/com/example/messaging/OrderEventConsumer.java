package com.example.messaging;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.IndexRequest;

import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Listens on the same Kafka topic the OrderService publishes to,
 * and indexes each ORDER_CREATED event into OpenSearch as a document.
 * Using the orderId as the OpenSearch document id makes indexing idempotent -
 * re-processing the same event overwrites instead of duplicating.
 */
@ApplicationScoped
public class OrderEventConsumer {

    private static final Logger LOG = Logger.getLogger(OrderEventConsumer.class);
    private static final String INDEX_NAME = "orders";

    @Inject
    OpenSearchClient openSearchClient;

    @Inject
    ObjectMapper objectMapper;

    @Incoming("order-events-in")
    public void consume(String payload) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> doc = objectMapper.readValue(payload, Map.class);
            Object orderId = doc.get("orderId");

            IndexRequest<Map<String, Object>> request = new IndexRequest.Builder<Map<String, Object>>()
                    .index(INDEX_NAME)
                    .id(orderId != null ? String.valueOf(orderId) : null)
                    .document(doc)
                    .build();

            openSearchClient.index(request);
            LOG.info("Indexed order event into OpenSearch, orderId=" + orderId);
        } catch (Exception e) {
            LOG.error("Failed to index order event into OpenSearch: " + payload, e);
        }
    }
}

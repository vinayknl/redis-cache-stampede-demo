package com.example.cachestampede.dto;

import java.io.Serializable;
import java.time.Instant;

/**
 * Simple record standing in for whatever "expensive to compute" payload
 * a real system would be caching (a DB query result, an aggregation,
 * a call to a downstream service, ...).
 */
public record Product(String id, String name, double price, Instant computedAt) implements Serializable {
}

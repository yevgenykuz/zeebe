/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.schema;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch.cluster.PutComponentTemplateRequest;
import co.elastic.clients.elasticsearch.indices.Alias;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.IndexTemplateSummary;
import co.elastic.clients.elasticsearch.indices.PutIndexTemplateRequest;
import co.elastic.clients.elasticsearch.indices.PutMappingRequest;
import co.elastic.clients.json.JsonpDeserializer;
import io.camunda.exporter.schema.descriptors.IndexDescriptor;
import io.camunda.exporter.schema.descriptors.IndexTemplateDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;

public class ElasticsearchEngineClient implements SearchEngineClient {
  private final ElasticsearchClient client;
  private final Logger log;
  private final ResourceRetriever resourceRetriever;

  public ElasticsearchEngineClient(
      final ElasticsearchClient client,
      final Logger log,
      final ResourceRetriever resourceRetriever) {
    this.client = client;
    this.log = log;
    this.resourceRetriever = resourceRetriever;
  }

  @Override
  public void createIndex(final IndexDescriptor indexDescriptor) {
    final CreateIndexRequest request = createIndexRequest(indexDescriptor);
    try {
      client.indices().create(request);
      log.debug("Index [{}] was successfully created", indexDescriptor.getIndexName());
    } catch (final IOException e) {
      log.error("Index [{}] was NOT created", indexDescriptor.getIndexName(), e);
      throw new RuntimeException(e);
    }
  }

  @Override
  public void createIndexTemplate(final IndexTemplateDescriptor templateDescriptor) {
    final PutIndexTemplateRequest request = putIndexTemplateRequest(templateDescriptor);

    try {
      client.indices().putIndexTemplate(request);
      log.debug("Template [{}] was successfully created", templateDescriptor.getTemplateName());
    } catch (final IOException e) {
      log.error("Template [{}] was NOT created", templateDescriptor.getTemplateName(), e);
      throw new RuntimeException(e);
    }
  }

  @Override
  public void createComponentTemplate(final String templateName, final String mappingsJson) {
    final PutComponentTemplateRequest request =
        putComponentTemplateRequest(templateName, mappingsJson);

    try {
      client.cluster().putComponentTemplate(request);
      log.debug("Component template [{}] was successfully created", templateName);
    } catch (final IOException e) {
      log.error("Component template [{}] was NOT created", templateName, e);
      throw new RuntimeException(e);
    }
  }

  @Override
  public void putMapping(final IndexDescriptor indexDescriptor, final String properties) {
    final PutMappingRequest request = putMappingRequest(indexDescriptor, properties);

    try {
      client.indices().putMapping(request);
      log.debug("Mapping in [{}] was successfully updated", indexDescriptor.getIndexName());
    } catch (final IOException e) {
      log.error("Mapping in [{}] was NOT updated", indexDescriptor.getIndexName(), e);
      throw new RuntimeException(e);
    }
  }

  private PutMappingRequest putMappingRequest(
      final IndexDescriptor indexDescriptor, final String properties) {

    try (final var propertiesStream = IOUtils.toInputStream(properties, StandardCharsets.UTF_8)) {
      return new PutMappingRequest.Builder()
          .index(indexDescriptor.getIndexName())
          .properties(deserializeJson(TypeMapping._DESERIALIZER, propertiesStream).properties())
          .build();
    } catch (final IOException e) {
      throw new RuntimeException(
          "Failed to load properties json into stream for put mapping into indexes matching the descriptor: ["
              + indexDescriptor
              + "]",
          e);
    }
  }

  private PutComponentTemplateRequest putComponentTemplateRequest(
      final String templateName, final String mappingsJson) {
    try (final var mappingsStream = IOUtils.toInputStream(mappingsJson, StandardCharsets.UTF_8)) {
      return new PutComponentTemplateRequest.Builder()
          .name(templateName)
          .template(
              t ->
                  t.mappings(
                      deserializeJson(IndexTemplateSummary._DESERIALIZER, mappingsStream)
                          .mappings()))
          .create(true)
          .build();
    } catch (final IOException e) {
      throw new RuntimeException(
          "Failed to load mappings json into stream for template: [" + templateName + "]", e);
    }
  }

  private <T> T deserializeJson(final JsonpDeserializer<T> deserializer, final InputStream json) {
    try (final var parser = client._jsonpMapper().jsonProvider().createParser(json)) {
      return deserializer.deserialize(parser, client._jsonpMapper());
    }
  }

  private InputStream descriptorMappings(final IndexDescriptor descriptor) {
    return resourceRetriever.getResourceAsStream(descriptor.getMappingsClasspathFilename());
  }

  private PutIndexTemplateRequest putIndexTemplateRequest(
      final IndexTemplateDescriptor indexTemplateDescriptor) {

    try (final var templateMappings = descriptorMappings(indexTemplateDescriptor)) {

      return new PutIndexTemplateRequest.Builder()
          .name(indexTemplateDescriptor.getTemplateName())
          .indexPatterns(indexTemplateDescriptor.getIndexPattern())
          .template(
              t ->
                  t.aliases(indexTemplateDescriptor.getAlias(), Alias.of(a -> a))
                      .mappings(
                          deserializeJson(IndexTemplateSummary._DESERIALIZER, templateMappings)
                              .mappings()))
          .composedOf(indexTemplateDescriptor.getComposedOf())
          .create(true)
          .build();
    } catch (final IOException e) {
      throw new RuntimeException(
          "Failed to load file "
              + indexTemplateDescriptor.getMappingsClasspathFilename()
              + " from classpath.",
          e);
    }
  }

  private CreateIndexRequest createIndexRequest(final IndexDescriptor indexDescriptor) {
    try (final var templateMappings = descriptorMappings(indexDescriptor)) {

      return new CreateIndexRequest.Builder()
          .index(indexDescriptor.getFullQualifiedName())
          .aliases(indexDescriptor.getAlias(), a -> a.isWriteIndex(false))
          .mappings(
              deserializeJson(IndexTemplateSummary._DESERIALIZER, templateMappings).mappings())
          .build();
    } catch (final IOException e) {
      throw new RuntimeException(
          "Failed to load file "
              + indexDescriptor.getMappingsClasspathFilename()
              + " from classpath.",
          e);
    }
  }
}

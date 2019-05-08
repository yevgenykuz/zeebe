/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.importing.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.service.es.schema.type.DecisionDefinitionType;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.camunda.optimize.service.es.schema.OptimizeIndexNameHelper.getOptimizeIndexAliasForType;
import static org.camunda.optimize.service.es.schema.type.DecisionDefinitionType.DECISION_DEFINITION_KEY;
import static org.camunda.optimize.service.es.schema.type.DecisionDefinitionType.DECISION_DEFINITION_VERSION;
import static org.camunda.optimize.service.es.schema.type.DecisionDefinitionType.DECISION_DEFINITION_XML;
import static org.camunda.optimize.service.es.schema.type.DecisionDefinitionType.TENANT_ID;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_DEFINITION_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.LIST_FETCH_LIMIT;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

@AllArgsConstructor
@Component
@Slf4j
public class DecisionDefinitionReader {
  private final ConfigurationService configurationService;
  private final ObjectMapper objectMapper;
  private final RestHighLevelClient esClient;

  public Optional<DecisionDefinitionOptimizeDto> getFullyImportedDecisionDefinition(
    final String decisionDefinitionKey,
    final String decisionDefinitionVersion,
    final String tenantId) {

    if (decisionDefinitionKey == null || decisionDefinitionVersion == null) {
      return Optional.empty();
    }

    final String validVersion = convertToValidVersion(decisionDefinitionKey, decisionDefinitionVersion);
    final BoolQueryBuilder query = QueryBuilders.boolQuery()
      .must(termQuery(DECISION_DEFINITION_KEY, decisionDefinitionKey))
      .must(termQuery(DECISION_DEFINITION_VERSION, validVersion))
      .must(existsQuery(DECISION_DEFINITION_XML));

    if (tenantId != null) {
      query.must(termQuery(TENANT_ID, tenantId));
    }

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
    searchSourceBuilder.query(query);
    searchSourceBuilder.size(1);
    SearchRequest searchRequest =
      new SearchRequest(getOptimizeIndexAliasForType(DECISION_DEFINITION_TYPE))
        .source(searchSourceBuilder);

    SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason = String.format(
        "Was not able to fetch decision definition with key [%s] and version [%s]",
        decisionDefinitionKey,
        decisionDefinitionVersion
      );
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    DecisionDefinitionOptimizeDto definitionOptimizeDto = null;
    if (searchResponse.getHits().getTotalHits() > 0L) {
      String responseAsString = searchResponse.getHits().getAt(0).getSourceAsString();
      definitionOptimizeDto = parseDecisionDefinition(responseAsString);
    }
    return Optional.ofNullable(definitionOptimizeDto);
  }

  public List<DecisionDefinitionOptimizeDto> getFullyImportedDecisionDefinitions(final boolean withXml) {
    return getDecisionDefinitions(true, withXml);
  }

  public List<DecisionDefinitionOptimizeDto> getDecisionDefinitions(final boolean fullyImported,
                                                                    final boolean withXml) {
    final String[] fieldsToExclude = withXml ? null : new String[]{DecisionDefinitionType.DECISION_DEFINITION_XML};

    final QueryBuilder query = fullyImported ? existsQuery(DECISION_DEFINITION_XML) : matchAllQuery();

    final SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(query)
      .size(LIST_FETCH_LIMIT)
      .fetchSource(null, fieldsToExclude);
    final SearchRequest searchRequest =
      new SearchRequest(getOptimizeIndexAliasForType(DECISION_DEFINITION_TYPE))
        .types(DECISION_DEFINITION_TYPE)
        .source(searchSourceBuilder)
        .scroll(new TimeValue(configurationService.getElasticsearchScrollTimeout()));

    final SearchResponse scrollResp;
    try {
      scrollResp = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      log.error("Was not able to retrieve decision definitions!", e);
      throw new OptimizeRuntimeException("Was not able to retrieve decision definitions!", e);
    }

    return ElasticsearchHelper.retrieveAllScrollResults(
      scrollResp,
      DecisionDefinitionOptimizeDto.class,
      objectMapper,
      esClient,
      configurationService.getElasticsearchScrollTimeout()
    );
  }

  private DecisionDefinitionOptimizeDto parseDecisionDefinition(final String responseAsString) {
    final DecisionDefinitionOptimizeDto definitionOptimizeDto;
    try {
      definitionOptimizeDto = objectMapper.readValue(responseAsString, DecisionDefinitionOptimizeDto.class);
    } catch (IOException e) {
      log.error("Could not read decision definition from Elasticsearch!", e);
      throw new OptimizeRuntimeException("Failure reading decision definition", e);
    }
    return definitionOptimizeDto;
  }

  private String convertToValidVersion(String decisionDefinitionKey, String decisionDefinitionVersion) {
    if (ReportConstants.ALL_VERSIONS.equals(decisionDefinitionVersion)) {
      return getLatestVersionToKey(decisionDefinitionKey);
    } else {
      return decisionDefinitionVersion;
    }
  }

  private String getLatestVersionToKey(String key) {
    log.debug("Fetching latest decision definition for key [{}]", key);
    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
      .query(termQuery(DECISION_DEFINITION_KEY, key))
      .sort(DECISION_DEFINITION_VERSION, SortOrder.DESC)
      .size(1);
    SearchRequest searchRequest =
      new SearchRequest(getOptimizeIndexAliasForType(DECISION_DEFINITION_TYPE))
        .types(DECISION_DEFINITION_TYPE)
        .source(searchSourceBuilder);

    SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
    } catch (IOException e) {
      String reason = String.format(
        "Was not able to fetch latest decision definition for key [%s]",
        key
      );
      log.error(reason, e);
      throw new OptimizeRuntimeException(reason, e);
    }

    if (searchResponse.getHits().getHits().length == 1) {
      Map<String, Object> sourceAsMap = searchResponse.getHits().getAt(0).getSourceAsMap();
      if (sourceAsMap.containsKey(DECISION_DEFINITION_VERSION)) {
        return sourceAsMap.get(DECISION_DEFINITION_VERSION).toString();
      }

    }
    throw new OptimizeRuntimeException("Unable to retrieve latest version for decision definition key: " + key);
  }

}

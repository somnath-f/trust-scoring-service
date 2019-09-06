package nimble.trust.engine.service;

import java.net.URI;
import java.util.List;

import eu.nimble.service.model.ubl.commonaggregatecomponents.PartyType;
import eu.nimble.service.model.ubl.extension.QualityIndicatorParameter;
//import nimble.trust.engine.restclient.IndexingClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.Lists;

import eu.nimble.utility.JsonSerializationUtility;
import nimble.trust.engine.collector.ProfileCompletnessCollector;
import nimble.trust.engine.domain.Agent;
import nimble.trust.engine.domain.TrustPolicy;
import nimble.trust.engine.model.pojo.TrustCriteria;
import nimble.trust.engine.model.vocabulary.Trust;
import nimble.trust.engine.module.Factory;
import nimble.trust.engine.restclient.IdentityServiceClient;
import nimble.trust.engine.service.interfaces.TrustSimpleManager;
import nimble.trust.engine.util.PolicyConverter;
import nimble.trust.web.dto.IdentifierNameTuple;

@Service
public class TrustCalculationService {

	private static Logger log = LoggerFactory.getLogger(TrustCalculationService.class);

	@Autowired
	private TrustScoreSync trustScoreSync;

	@Autowired
	private TrustPolicyService trustPolicyService;

	@Autowired
	private AgentService agentService;
	
	@Autowired
	private IdentityServiceClient identityServiceClient;

//	@Autowired
//	private IndexingClient indexingClient;

	@Autowired
	private TrustProfileService trustProfileService;
//	@Autowired
//	private ProfileCompletnessCollector completnessCollector;
	

	@Value("${app.trust.trustScore.syncWithCatalogService}")
	private Boolean syncWithCatalogService;

	public void score(String partyId) {

		final String bearerToken = SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString();
		// get profile and policy and calculate;
		final TrustSimpleManager trustManager = Factory.createInstance(TrustSimpleManager.class);
		TrustPolicy trustPolicy = trustPolicyService.findGlobalTRustPolicy();
		TrustCriteria criteria = PolicyConverter.fromPolicyToCriteria(trustPolicy);
		Double trustScore = null;
		try {
			trustScore = trustManager.obtainTrustIndex(URI.create(Trust.getURI() + partyId), criteria);

			agentService.updateTrustScore(partyId, trustScore);

//			if (syncWithCatalogService) {
//				PartyType partyType = trustProfileService.createPartyType(partyId);
//				eu.nimble.service.model.solr.party.PartyType indexParty =  indexingClient.getParty(partyId,bearerToken);
//
//				// get trust scores
//				partyType.getQualityIndicator().forEach(qualityIndicator -> {
//					if(qualityIndicator.getQualityParameter() != null && qualityIndicator.getQuantity() != null) {
//						if(qualityIndicator.getQualityParameter().contentEquals(QualityIndicatorParameter.COMPANY_RATING.toString())) {
//							indexParty.setTrustRating(qualityIndicator.getQuantity().getValue().doubleValue());
//						} else if(qualityIndicator.getQualityParameter().contentEquals(QualityIndicatorParameter.TRUST_SCORE.toString())) {
//							indexParty.setTrustScore(qualityIndicator.getQuantity().getValue().doubleValue());
//						} else if(qualityIndicator.getQualityParameter().contentEquals(QualityIndicatorParameter.DELIVERY_PACKAGING.toString())) {
//							indexParty.setTrustDeliveryPackaging(qualityIndicator.getQuantity().getValue().doubleValue());
//						} else if(qualityIndicator.getQualityParameter().contentEquals(QualityIndicatorParameter.FULFILLMENT_OF_TERMS.toString())) {
//							indexParty.setTrustFullfillmentOfTerms(qualityIndicator.getQuantity().getValue().doubleValue());
//						} else if(qualityIndicator.getQualityParameter().contentEquals(QualityIndicatorParameter.SELLER_COMMUNICATION.toString())) {
//							indexParty.setTrustSellerCommunication(qualityIndicator.getQuantity().getValue().doubleValue());
//						} else if(qualityIndicator.getQualityParameter().contentEquals(QualityIndicatorParameter.NUMBER_OF_TRANSACTIONS.toString())) {
//							indexParty.setTrustNumberOfTransactions(qualityIndicator.getQuantity().getValue().doubleValue());
//						} else if(qualityIndicator.getQualityParameter().contentEquals(QualityIndicatorParameter.TRADING_VOLUME.toString())) {
//							indexParty.setTrustTradingVolume(qualityIndicator.getQuantity().getValue().doubleValue());
//						}
//					}
//				});
//				indexingClient.setParty(indexParty,bearerToken);
//			}
			log.info("trust score of party with ID " + partyId + " is updated to " + trustScore);
		} catch (Exception e) {
			log.error("Exception", e);
		}
	}

	/**
	 * 
	 */
	@Async
	public void scoreBatch() {
		int pageNumber = 0;
		int pageLimit = 100;
		Page<Agent> page;
		do {
			page = agentService.findAll(new PageRequest(pageNumber, pageLimit));
			pageNumber++;
			List<Agent> list = page.getContent();
			for (Agent agent : list) {
				score(agent.getAltId());
			}
		} while (!page.isLast());
	}

	/**
	 * 
	 */
	@Async
	public void createAllAndScoreBatch() {

		final String bearerToken = SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString();

		try {
			feign.Response response = identityServiceClient.getAllPartyIds(bearerToken, Lists.newArrayList());
			if (response.status() == HttpStatus.OK.value()) {
				List<IdentifierNameTuple> tuples = JsonSerializationUtility.deserializeContent(
						response.body().asInputStream(), new TypeReference<List<IdentifierNameTuple>>() {
						});
				for (IdentifierNameTuple t : tuples) {
//					completnessCollector.fetchProfileCompletnessValues(t.getIdentifier(), true);
//					trustScoreSync.syncWithCatalogService(t.getIdentifier());

				}
			} else {
				log.info("GetAllPartyIds request to identity service failed due: "
						+ new feign.codec.StringDecoder().decode(response, String.class));
			}
		} catch (Exception e) {
			log.error("CreateAllAndScoreBatch failed or internal error happened", e);
		}
	}

}

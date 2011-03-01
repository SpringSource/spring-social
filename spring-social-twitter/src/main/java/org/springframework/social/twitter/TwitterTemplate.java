/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.social.twitter;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.social.AccountNotConnectedException;
import org.springframework.social.ResponseStatusCodeTranslator;
import org.springframework.social.SocialException;
import org.springframework.social.oauth1.ProtectedResourceClientFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.NumberUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.web.client.RestTemplate;

/**
 * This is the central class for interacting with Twitter.
 * <p>
 * Most (not all) Twitter operations require OAuth authentication. To perform
 * such operations, {@link TwitterTemplate} must be constructed with the minimal
 * amount of information required to sign requests to Twitter's API with an
 * OAuth <code>Authorization</code> header.
 * </p>
 * <p>
 * There are a few operations, such as searching, that do not require OAuth
 * authentication. In those cases, you may use a {@link TwitterTemplate} that is
 * created through the default constructor and without any OAuth details.
 * Attempts to perform secured operations through such an instance, however,
 * will result in {@link AccountNotConnectedException} being thrown.
 * </p>
 * @author Craig Walls
 */
public class TwitterTemplate implements TwitterApi {

	private final RestTemplate restTemplate;
	
	private final ResponseStatusCodeTranslator statusCodeTranslator;

	/**
	 * Create a new instance of TwitterTemplate.
	 * This constructor creates a new TwitterTemplate able to perform unauthenticated operations against Twitter's API.
	 * Some operations, such as search, do not require OAuth authentication.
	 * A TwitterTemplate created with this constructor will support those operations.
	 * Those operations requiring authentication will throw {@link AccountNotConnectedException}.
	 */
	public TwitterTemplate() {
		restTemplate = new RestTemplate();
		restTemplate.setErrorHandler(new TwitterErrorHandler());
		this.statusCodeTranslator = new TwitterResponseStatusCodeTranslator();
	}

	/**
	 * Create a new instance of TwitterTemplate.
	 * @param apiKey the application's API key
	 * @param apiSecret the application's API secret
	 * @param accessToken an access token acquired through OAuth authentication with LinkedIn
	 * @param accessTokenSecret an access token secret acquired through OAuth authentication with LinkedIn
	 */
	public TwitterTemplate(String apiKey, String apiSecret, String accessToken, String accessTokenSecret) {
		restTemplate = ProtectedResourceClientFactory.create(apiKey, apiSecret, accessToken, accessTokenSecret);
		restTemplate.setErrorHandler(new TwitterErrorHandler());
		this.statusCodeTranslator = new TwitterResponseStatusCodeTranslator();
	}

	public String getProfileId() {
		Map<?, ?> response = restTemplate.getForObject(VERIFY_CREDENTIALS_URL, Map.class);
		return (String) response.get("screen_name");
	}

	public TwitterProfile getUserProfile() {
		return getUserProfile(getProfileId());
	}

	public TwitterProfile getUserProfile(String screenName) {
		Map<?, ?> response = restTemplate.getForObject(USER_PROFILE_URL + "?screen_name={screenName}", Map.class, screenName);
		return getProfileFromResponseMap(response);
	}

	public TwitterProfile getUserProfile(long userId) {
		Map<?, ?> response = restTemplate.getForObject(USER_PROFILE_URL + "?user_id={userId}", Map.class, userId);
		return getProfileFromResponseMap(response);
	}

	private TwitterProfile getProfileFromResponseMap(Map<?, ?> response) {
		TwitterProfile profile = new TwitterProfile();
		profile.setId(Long.valueOf(String.valueOf(response.get("id"))).longValue());
		profile.setScreenName(String.valueOf(response.get("screen_name")));
		profile.setName(String.valueOf(response.get("name")));
		profile.setDescription(String.valueOf(response.get("description")));
		profile.setLocation(String.valueOf(response.get("location")));
		profile.setUrl(String.valueOf(response.get("url")));
		profile.setProfileImageUrl(String.valueOf(response.get("profile_image_url")));
		profile.setCreatedDate(toDate(String.valueOf(response.get("created_at")), timelineDateFormat));
		return profile;
	}

	public List<String> getFriends(String screenName) {
		List<Map<String, String>> response = restTemplate.getForObject(FRIENDS_STATUSES_URL, List.class, Collections.singletonMap("screen_name", screenName));
		List<String> friends = new ArrayList<String>(response.size());
		for (Map<String, String> item : response) {
			friends.add(item.get("screen_name"));
		}
		return friends;
	}
	
	public List<String> getFollowers(String screenName) {
	    List<Map<String, String>> response = restTemplate.getForObject(FOLLOWERS_STATUSES_URL, List.class, Collections.singletonMap("screen_name", screenName));
	    List<String> followers = new ArrayList<String>(response.size());
	    for(Map<String, String> item : response) {
	        followers.add(item.get("screen_name"));
	    }
	    
	    return followers;
	    
	}
	
	public String follow(String screenName) {
	    ResponseEntity<Map> response = restTemplate.postForEntity(FOLLOW_URL, "", Map.class, Collections.singletonMap("screen_name", screenName));
	    handleResponseErrors(response);
	    Map<String, Object> body = response.getBody();
	    return (String) body.get("screen_name");
	    
	}

	public void updateStatus(String message) {
		updateStatus(message, new StatusDetails());
	}

	public void updateStatus(String message, StatusDetails details) {
		MultiValueMap<String, Object> tweetParams = new LinkedMultiValueMap<String, Object>();
		tweetParams.add("status", message);
		tweetParams.setAll(details.toParameterMap());
		ResponseEntity<Map> response = restTemplate.postForEntity(TWEET_URL, tweetParams, Map.class);
		handleResponseErrors(response);
	}

	public void retweet(long tweetId) {
		ResponseEntity<Map> response = restTemplate.postForEntity(RETWEET_URL, "", Map.class, Collections.singletonMap("tweet_id", Long.toString(tweetId)));
		handleResponseErrors(response);
	}

	public List<Tweet> getMentions() {
		List response = restTemplate.getForObject(MENTIONS_URL, List.class);
		List<Map<String, Object>> results = response;
		List<Tweet> tweets = new ArrayList<Tweet>();
		for (Map<String, Object> item : results) {
			tweets.add(populateTweetFromTimelineItem(item));
		}
		return tweets;
	}

	public List<DirectMessage> getDirectMessagesReceived() {
		ResponseEntity<List> response = restTemplate.getForEntity(DIRECT_MESSAGES_URL, List.class);
		List<Map<String, Object>> results = response.getBody();
		List<DirectMessage> messages = new ArrayList<DirectMessage>();
		for (Map<String, Object> item : results) {
			DirectMessage message = new DirectMessage();
			message.setId(Long.valueOf(String.valueOf(item.get("id"))));
			message.setText(String.valueOf(item.get("text")));
			message.setSenderId(Long.valueOf(String.valueOf(item.get("sender_id"))));
			message.setSenderScreenName(String.valueOf(item.get("sender_screen_name")));
			message.setRecipientId(Long.valueOf(String.valueOf(item.get("recipient_id"))));
			message.setRecipientScreenName(String.valueOf(item.get("recipient_screen_name")));
			message.setCreatedAt(toDate(ObjectUtils.nullSafeToString(item.get("created_at")), timelineDateFormat));
			messages.add(message);
		}
		return messages;
	}

	public void sendDirectMessage(String toScreenName, String text) {
		MultiValueMap<String, Object> dmParams = new LinkedMultiValueMap<String, Object>();
		dmParams.add("screen_name", toScreenName);
		sendDirectMessage(text, dmParams);
	}

	public void sendDirectMessage(long toUserId, String text) {
		MultiValueMap<String, Object> dmParams = new LinkedMultiValueMap<String, Object>();
		dmParams.add("user_id", String.valueOf(toUserId));
		sendDirectMessage(text, dmParams);
	}

	private void sendDirectMessage(String text, MultiValueMap<String, Object> dmParams) {
		dmParams.add("text", text);
		ResponseEntity<Map> response = restTemplate.postForEntity(SEND_DIRECT_MESSAGE_URL, dmParams, Map.class);
		handleResponseErrors(response);
	}

	public List<Tweet> getPublicTimeline() {
		List response = restTemplate.getForObject(PUBLIC_TIMELINE_URL, List.class);
		return extractTimelineTweetsFromResponse(response);
	}

	public List<Tweet> getHomeTimeline() {
		List response = restTemplate.getForObject(HOME_TIMELINE_URL, List.class);
		return extractTimelineTweetsFromResponse(response);
	}

	public List<Tweet> getFriendsTimeline() {
		List response = restTemplate.getForObject(FRIENDS_TIMELINE_URL, List.class);
		return extractTimelineTweetsFromResponse(response);
	}

	public List<Tweet> getUserTimeline() {
		List response = restTemplate.getForObject(USER_TIMELINE_URL, List.class);
		return extractTimelineTweetsFromResponse(response);
	}

	public List<Tweet> getUserTimeline(String screenName) {
		List response = restTemplate.getForObject(USER_TIMELINE_URL + "?screen_name={screenName}", List.class, screenName);
		return extractTimelineTweetsFromResponse(response);
	}

	public List<Tweet> getUserTimeline(long userId) {
		List response = restTemplate.getForObject(USER_TIMELINE_URL + "?user_id={userId}", List.class, userId);
		return extractTimelineTweetsFromResponse(response);
	}

	public SearchResults search(String query) {
		return search(query, 1, DEFAULT_RESULTS_PER_PAGE, 0, 0);
	}

	public SearchResults search(String query, int page, int resultsPerPage) {
		return search(query, page, resultsPerPage, 0, 0);
	}

	public SearchResults search(String query, int page, int resultsPerPage, int sinceId, int maxId) {
		Map<String, String> parameters = new HashMap<String, String>();
		parameters.put("query", query);
		parameters.put("rpp", String.valueOf(resultsPerPage));
		parameters.put("page", String.valueOf(page));
		String searchUrl = SEARCH_URL;
		if (sinceId > 0) {
			searchUrl += "&since_id={since}";
			parameters.put("since", String.valueOf(sinceId));
		}
		if (maxId > 0) {
			searchUrl += "&max_id={max}";
			parameters.put("max", String.valueOf(maxId));
		}
		ResponseEntity<Map> response = restTemplate.getForEntity(searchUrl, Map.class, parameters);
		// handleResponseErrors(response);
		Map<String, Object> resultsMap = response.getBody();
		List<Map<String, Object>> items = (List<Map<String, Object>>) resultsMap.get("results");
		List<Tweet> tweets = new ArrayList<Tweet>(resultsMap.size());
		for (Map<String, Object> item : items) {
			tweets.add(populateTweetFromSearchResults(item));
		}
		return buildSearchResults(resultsMap, tweets);
	}

	// subclassing hooks
	
	protected RestTemplate getRestTemplate() {
		return restTemplate;
	}
	
	// internal helpers
	
	private SearchResults buildSearchResults(Map<String, Object> response, List<Tweet> tweets) {
		Number maxId = response.containsKey("max_id") ? (Number) response.get("max_id") : 0;
		Number sinceId = response.containsKey("since_id") ? (Number) response.get("since_id") : 0;
		return new SearchResults(tweets, maxId.longValue(), sinceId.longValue(), response.get("next_page") == null);
	}

	private List<Tweet> extractTimelineTweetsFromResponse(List response) {
		List<Map<String, Object>> results = response;
		List<Tweet> tweets = new ArrayList<Tweet>();
		for (Map<String, Object> item : results) {
			tweets.add(populateTweetFromTimelineItem(item));
		}
		return tweets;
	}

	private Tweet populateTweetFromTimelineItem(Map<String, Object> item) {
		Tweet tweet = new Tweet();
		tweet.setId(Long.valueOf(String.valueOf(item.get("id"))));
		tweet.setText(String.valueOf(item.get("text")));
		tweet.setFromUser(String.valueOf(((Map<String, Object>) item.get("user")).get("screen_name")));
		tweet.setFromUserId(Long.valueOf(String.valueOf(((Map<String, Object>) item.get("user")).get("id"))));
		tweet.setProfileImageUrl(String.valueOf(((Map<String, Object>) item.get("user")).get("profile_image_url")));
		tweet.setSource(String.valueOf(item.get("source")));
		Object toUserId = item.get("in_reply_to_user_id");
		tweet.setToUserId(toUserId != null ? Long.valueOf(String.valueOf(toUserId)) : null);
		tweet.setCreatedAt(toDate(ObjectUtils.nullSafeToString(item.get("created_at")), timelineDateFormat));
		return tweet;
	}

	private Tweet populateTweetFromSearchResults(Map<String, Object> item) {
		Tweet tweet = new Tweet();
		tweet.setId(NumberUtils.parseNumber(ObjectUtils.nullSafeToString(item.get("id")), Long.class));
		tweet.setFromUser(ObjectUtils.nullSafeToString(item.get("from_user")));
		tweet.setText(ObjectUtils.nullSafeToString(item.get("text")));
		tweet.setCreatedAt(toDate(ObjectUtils.nullSafeToString(item.get("created_at")), searchDateFormat));
		tweet.setFromUserId(NumberUtils.parseNumber(ObjectUtils.nullSafeToString(item.get("from_user_id")), Long.class));
		Object toUserId = item.get("to_user_id");
		if (toUserId != null) {
			tweet.setToUserId(NumberUtils.parseNumber(ObjectUtils.nullSafeToString(toUserId), Long.class));
		}
		tweet.setLanguageCode(ObjectUtils.nullSafeToString(item.get("iso_language_code")));
		tweet.setProfileImageUrl(ObjectUtils.nullSafeToString(item.get("profile_image_url")));
		tweet.setSource(ObjectUtils.nullSafeToString(item.get("source")));
		return tweet;
	}

	private final DateFormat searchDateFormat = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z", Locale.ENGLISH);
	private final DateFormat timelineDateFormat = new SimpleDateFormat("EEE MMM dd HH:mm:ss ZZZZZ yyyy", Locale.ENGLISH);

	private Date toDate(String dateString, DateFormat dateFormat) {
		try {
			return dateFormat.parse(dateString);
		} catch (ParseException e) {
			return null;
		}
	}

	private void handleResponseErrors(ResponseEntity<Map> response) {
		SocialException exception = statusCodeTranslator.translate(response);
		if (exception != null) {
			throw exception;
		}
	}

	static final int DEFAULT_RESULTS_PER_PAGE = 50;

	static final String API_URL_BASE = "https://api.twitter.com/1/";
	static final String SEARCH_API_URL_BASE = "https://search.twitter.com";
	static final String VERIFY_CREDENTIALS_URL = API_URL_BASE + "account/verify_credentials.json";
	static final String USER_PROFILE_URL = API_URL_BASE + "users/show.json";
	static final String FRIENDS_STATUSES_URL = API_URL_BASE + "statuses/friends.json?screen_name={screen_name}";
	static final String FOLLOWERS_STATUSES_URL = API_URL_BASE + "statuses/followers.json?screen_name={screen_name}";
	static final String SEARCH_URL = SEARCH_API_URL_BASE + "/search.json?q={query}&rpp={rpp}&page={page}";
	static final String TWEET_URL = API_URL_BASE + "statuses/update.json";
	static final String RETWEET_URL = API_URL_BASE + "/statuses/retweet/{tweet_id}.json";
	static final String MENTIONS_URL = API_URL_BASE + "statuses/mentions.json";
	static final String DIRECT_MESSAGES_URL = API_URL_BASE + "direct_messages.json";
	static final String SEND_DIRECT_MESSAGE_URL = API_URL_BASE + "direct_messages/new.json";
	static final String PUBLIC_TIMELINE_URL = API_URL_BASE + "statuses/public_timeline.json";
	static final String HOME_TIMELINE_URL = API_URL_BASE + "statuses/home_timeline.json";
	static final String FRIENDS_TIMELINE_URL = API_URL_BASE + "statuses/friends_timeline.json";
	static final String USER_TIMELINE_URL = API_URL_BASE + "statuses/user_timeline.json";
	
	static final String FOLLOW_URL = API_URL_BASE + "friendships/create.json?screen_name={screen_name}";
	static final String UNFOLLOW_URL = API_URL_BASE + "friendships/destroy.json?screen_name={screen_name}";
}

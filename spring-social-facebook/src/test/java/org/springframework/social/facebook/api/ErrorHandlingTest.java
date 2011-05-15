/*
 * Copyright 2011 the original author or authors.
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
package org.springframework.social.facebook.api;

import static org.junit.Assert.*;
import static org.springframework.http.HttpMethod.*;
import static org.springframework.social.test.client.RequestMatchers.*;
import static org.springframework.social.test.client.ResponseCreators.*;

import org.junit.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.social.BadCredentialsException;
import org.springframework.social.facebook.api.impl.FacebookTemplate;
import org.springframework.social.test.client.MockRestServiceServer;

public class ErrorHandlingTest extends AbstractFacebookApiTest {

	@Test
	public void insufficientPrivileges() {
		try {
			mockServer.expect(requestTo("https://graph.facebook.com/193482154020832/declined"))
				.andExpect(method(POST))
				.andExpect(header("Authorization", "OAuth someAccessToken"))
				.andRespond(withResponse(new ClassPathResource("testdata/error-insufficient-privilege.json", getClass()), responseHeaders, HttpStatus.FORBIDDEN, ""));
			facebook.eventOperations().declineInvitation("193482154020832");
			fail();
		} catch (InsufficientPermissionException e) {
			assertEquals("(#299) Requires extended permission: rsvp_event", e.getMessage());
			assertEquals("rsvp_event", e.getRequiredPermission());
		}
	}
	
	@Test
	public void notAFriend() {
		try {
			mockServer.expect(requestTo("https://graph.facebook.com/119297590579/members/100001387295207"))
				.andExpect(method(POST))
				.andExpect(header("Authorization", "OAuth someAccessToken"))
				.andRespond(withResponse(new ClassPathResource("testdata/error-not-a-friend.json", getClass()), responseHeaders, HttpStatus.INTERNAL_SERVER_ERROR, ""));
			facebook.friendOperations().addToFriendList("119297590579", "100001387295207");
			fail();
		} catch (NotAFriendException e) {
			assertEquals("The member must be a friend of the current user.", e.getMessage());
		}		
	}
	
	@Test
	public void unknownPath() {
		try {
			mockServer.expect(requestTo("https://graph.facebook.com/me/boguspath"))
				.andExpect(method(GET))
				.andExpect(header("Authorization", "OAuth someAccessToken"))
				.andRespond(withResponse(new ClassPathResource("testdata/error-unknown-path.json", getClass()), responseHeaders, HttpStatus.BAD_REQUEST, ""));
			facebook.fetchConnections("me", "boguspath", String.class);
			fail();
		} catch (GraphAPIException e) {
			assertEquals("Unknown path components: /boguspath", e.getMessage());
		}
	}
	
	@Test
	public void notTheOwner() {
		try {
			mockServer.expect(requestTo("https://graph.facebook.com/1234567890"))
				.andExpect(method(POST))
				.andExpect(header("Authorization", "OAuth someAccessToken"))
				.andExpect(body("method=delete"))
				.andRespond(withResponse(new ClassPathResource("testdata/error-not-the-owner.json", getClass()), responseHeaders, HttpStatus.INTERNAL_SERVER_ERROR, ""));
			facebook.friendOperations().deleteFriendList("1234567890");
			fail();
		} catch (OwnershipException e) {
			assertEquals("User must be an owner of the friendlist", e.getMessage());
		}		
	}
	
	@Test
	public void unknownAlias_HTTP200() {
		// yes, Facebook really does return this error as HTTP 200 (probably should be 404)
		try {
			mockServer.expect(requestTo("https://graph.facebook.com/dummyalias"))
				.andExpect(method(GET))
				.andExpect(header("Authorization", "OAuth someAccessToken"))
				.andRespond(withResponse(new ClassPathResource("testdata/error-unknown-alias.json", getClass()), responseHeaders, HttpStatus.OK, ""));
			facebook.fetchObject("dummyalias", FacebookProfile.class);
			fail("Expected GraphAPIException when fetching an unknown object alias");
		} catch (GraphAPIException e) {
			assertEquals("(#803) Some of the aliases you requested do not exist: dummyalias", e.getMessage());
		}				
	}
	
	@Test
	public void currentUser_noAccessToken() {
		FacebookTemplate facebook = new FacebookTemplate(); // use anonymous FacebookTemplate in this test
		MockRestServiceServer mockServer = MockRestServiceServer.createServer(facebook.getRestTemplate());
		try {
			mockServer.expect(requestTo("https://graph.facebook.com/me"))
				.andExpect(method(GET))
				.andRespond(withResponse(new ClassPathResource("testdata/error-current-user-no-token.json", getClass()), responseHeaders, HttpStatus.BAD_REQUEST, ""));
			facebook.userOperations().getUserProfile();
			fail("Expected BadCredentialsException when fetching an unknown object alias");
		} catch (BadCredentialsException e) {
			assertEquals("An active access token must be used to query information about the current user.", e.getMessage());
		}						
	}
	
}

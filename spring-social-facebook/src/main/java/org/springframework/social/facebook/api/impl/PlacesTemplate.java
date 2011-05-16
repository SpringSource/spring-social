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
package org.springframework.social.facebook.api.impl;

import java.util.List;

import org.springframework.social.facebook.api.Checkin;
import org.springframework.social.facebook.api.GraphApi;
import org.springframework.social.facebook.api.PlacesOperations;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

class PlacesTemplate implements PlacesOperations {
	
	private final GraphApi graphApi;

	public PlacesTemplate(GraphApi graphApi) {
		this.graphApi = graphApi;
	}
	
	public List<Checkin> getCheckins() {
		return getCheckins("me");
	}

	public List<Checkin> getCheckins(String objectId) {
		return graphApi.fetchConnections(objectId, "checkins", CheckinList.class).getList();
	}

	public Checkin getCheckin(String checkinId) {
		return graphApi.fetchObject(checkinId, Checkin.class);
	}
	
	public String checkin(String placeId, double latitude, double longitude) {
		return checkin(placeId, latitude, longitude, null, (String[]) null);
	}
	
	public String checkin(String placeId, double latitude, double longitude, String message, String... tags) {
		MultiValueMap<String, Object> data = new LinkedMultiValueMap<String, Object>();
		data.set("place", placeId);
		data.set("coordinates", "{\"latitude\":\"" + latitude+"\",\"longitude\":\"" + longitude + "\"}");
		if(message != null) {
			data.set("message", message);
		}
		
		if(tags != null && tags.length > 0) {
			String tagsValue = tags[0];
			for(int i=1; i < tags.length; i++) {
				tagsValue += "," + tags[i];
			}
			data.set("tags", tagsValue);
		}
		return graphApi.publish("me", "checkins", data);
	}
}

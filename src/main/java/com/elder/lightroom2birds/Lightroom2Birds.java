package com.elder.lightroom2birds;

import java.io.File;
import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.support.BasicAuthorizationInterceptor;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import com.tgelder.birds.Bird;
import com.tgelder.birds.BirdResource;
import com.tgelder.birds.Photo;
import com.tgelder.birds.PhotoResource;

public class Lightroom2Birds {

	private final RestTemplate restTemplate = new RestTemplate();
	private Connection connection=null;
	private Statement statement;
	
	Lightroom2Birds() throws ClassNotFoundException, SQLException {
		
	
		Class.forName("org.sqlite.JDBC");
	
		
	    connection = DriverManager.getConnection("jdbc:sqlite:/media/thomas/Storage/Documents/Photos/Lightroom/Lightroom Catalog.lrcat");
	    statement = connection.createStatement();
	    statement.setQueryTimeout(30);  // set timeout to 30 sec.
                 
		restTemplate.getMessageConverters().add(new ByteArrayHttpMessageConverter());
		restTemplate.getInterceptors().add(
	      		  new BasicAuthorizationInterceptor("telder", "whathaburt"));
	}

	void close() throws SQLException {
		if (connection!=null) {
			connection.close();
		}
	}
	
	private void uploadPhoto(String path) {

		MultiValueMap<String, Object> params = new LinkedMultiValueMap<String, Object>();
		params.add("file", new FileSystemResource(path));
		HttpHeaders httpHeaders = new HttpHeaders();
		httpHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);
		HttpEntity requestEntity = new HttpEntity<>(params, httpHeaders);
		restTemplate.exchange("http://localhost:8080/files", HttpMethod.POST, requestEntity, String.class);
	}
	
	BirdResource addBird(String name) {
		Bird bird = new Bird(name);
        
        URI uri = restTemplate.postForLocation("http://localhost:8080/birds/", bird);
        
        return restTemplate.getForObject(uri, BirdResource.class);
	}
	
	PhotoResource addPhoto(String path, String location, Date timestamp) {
		Photo photo = new Photo(new File(path).getName(),location,timestamp);
        
        URI uri = restTemplate.postForLocation("http://localhost:8080/photos/", photo);
        
        uploadPhoto(path);
        
        return restTemplate.getForObject(uri, PhotoResource.class);
	}
	
	void setFavourite(BirdResource bird, PhotoResource favourite) {
		
		bird.getBird().setFavourite(favourite.getPhoto());
		restTemplate.put(bird.getLink("self").getHref(),bird.getBird());
	}
	
	Map<Integer,String> getBirds() throws SQLException {
		Map<Integer,String> birds = new HashMap<Integer,String> ();
		
		ResultSet resultSet = statement.executeQuery(
				   "select k.id_local, k.name"
				+ " from AGLibraryKeyword k"
				+ " left join AGLibraryKeyword p"
				+ " on k.parent = p.id_local"
				+ " where p.name = 'Bird'"
				+ " and k.name != 'Unknown'");
		
		
		String inString = "";
		String masterInString = "";
		
		while(resultSet.next()) {
			birds.put(resultSet.getInt("id_local"), resultSet.getString("name"));
		}
		
		Map<Integer,String> birds2 = new HashMap<Integer,String> ();
		
		for (Map.Entry<Integer, String> bird : birds.entrySet()) {
								
			inString = bird.getKey().toString();
			
			while (inString.length()>0) {
				
				
				resultSet = statement.executeQuery(
				    "select k.id_local" +
					" from AGLibraryKeyword k" +
					" where k.parent in ("+inString+")");
				
				
				if (masterInString.length()>0) {
					masterInString += ",";
				}
				masterInString += inString;
				
				inString = "";
				
				while(resultSet.next()) {
					birds2.put(resultSet.getInt("id_local"), bird.getValue());
					
					if (inString.length()>0) {
						inString += ",";
					}
					inString += resultSet.getInt("id_local");

				}
				
			}
			
		}
		
		birds.putAll(birds2);
		
		return birds2;
	}
	
}

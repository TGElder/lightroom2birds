package com.elder.lightroom2birds;

import java.io.File;
import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
	
	Lightroom2Birds(String username, String password) throws ClassNotFoundException, SQLException {
		
	
		Class.forName("org.sqlite.JDBC");
	
		
	    connection = DriverManager.getConnection("jdbc:sqlite:/media/thomas/Storage/Documents/Photos/Lightroom/Lightroom Catalog.lrcat");
	    statement = connection.createStatement();
	    statement.setQueryTimeout(30);  // set timeout to 30 sec.
                 
		restTemplate.getMessageConverters().add(new ByteArrayHttpMessageConverter());
		restTemplate.getInterceptors().add(
	      		  new BasicAuthorizationInterceptor(username,password));
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
                
        return restTemplate.getForObject(uri, PhotoResource.class);
	}
	
	void updateBird(BirdResource bird) {
		
		restTemplate.put(bird.getLink("self").getHref(),bird.getBird());
	}

	
	Map<Integer,List<String>> getHierarchy(String top) throws SQLException {
		
		ResultSet resultSet = statement.executeQuery(
				   "select k.id_local"
				+ " from AGLibraryKeyword k"
				+ " where k.name = '"+top+"'");
		
		Map<Integer,List<String>> out = new HashMap<> ();
		List<Integer> open = new ArrayList<> ();

		while (resultSet.next()) {
			open.add(resultSet.getInt("id_local"));
		}
		
		while (!open.isEmpty()) {
			Integer parent = open.get(0);			
			open.remove(0);
			
			List<String> parentLevels = out.get(parent);
			
			if (parentLevels==null) {
				parentLevels = new ArrayList<>();
			}

			resultSet = statement.executeQuery(
					   "select k.id_local, k.name"
					+ " from AGLibraryKeyword k"
					+ " where k.parent = "+parent);
			
			while(resultSet.next()) {
				List<String> levels = new ArrayList<> (parentLevels);
				levels.add(resultSet.getString("name"));
				
				out.put(resultSet.getInt("id_local"), levels);
				
				open.add(resultSet.getInt("id_local"));
			}
			
		}
			
		return out;
	}
	
	Map<Integer,BirdResource> getBirds() throws SQLException {
		Map<Integer,BirdResource> out = new HashMap<> ();
		
		Map<Integer,List<String>> strings = getHierarchy("Bird");
		
		Map<String,BirdResource> birds = new HashMap<> ();
		
		for (Map.Entry<Integer, List<String>> stringsEntry: strings.entrySet()) {
			
			String birdString = stringsEntry.getValue().get(0);
			
			if (!birdString.equals("Unknown")) {
			
				BirdResource bird = birds.get(birdString);
					
				if (bird==null) {
					bird = addBird(birdString);
					birds.put(birdString, bird);
				}
			
				out.put(stringsEntry.getKey(), bird);
			}
		
		}
		
		return out;
		
	}
	
	Collection<PhotoResource> getPhotos(Map<Integer,BirdResource> birds) throws SQLException {
		
		String tags = birds.keySet().toString()
				.replaceAll("\\[", "")
				.replaceAll("\\]", "");
		
		
		ResultSet resultSet = statement.executeQuery(
				   "select i.id_local,"
				+ " lki.tag,"
				+ " i.captureTime,"
				+ " lf.baseName,"
				+ " lf2.pathFromRoot"
				+ " from AGLibraryKeywordImage lki"
				+ " inner join AgLibraryCollectionImage lci"
				+ " on lci.image = lki.image"
				+ " left join Adobe_images i"
				+ " on i.id_local = lki.image"
				+ " left join AgLibraryFile lf"
				+ " on lf.id_local = i.rootFile"
				+ " left join AGLibraryFolder lf2"
				+ " on lf2.id_local = lf.folder"
				+ " where lki.tag in ("+tags+")"
				+ " and lci.collection = ("
				+ "     select id_local"
				+ "     from AgLibraryCollection"
				+ "     where name = 'Good'"
				+ " )"
				);
		

		Collection<PhotoRow> rows = new HashSet<PhotoRow> ();

		while (resultSet.next()) {
			rows.add(new PhotoRow(resultSet));			
		}
		
		Map<Integer,PhotoResource> out = new HashMap<> ();
				
		Map<Integer,List<String>> locations = getHierarchy("Location");
		
		tags = locations.keySet().toString()
				.replaceAll("\\[", "")
				.replaceAll("\\]", "");
		
		Collection<PhotoResource> favourites = new HashSet<>();
		
		for (PhotoRow row : rows) {
			
			PhotoResource photo = out.get(row.image);
			
			if (photo==null) {
				resultSet = statement.executeQuery(
						  " select k.name"
						+ " from AGLibraryKeywordImage ki"
						+ " left join AGLibraryKeyword k"
						+ " on k.id_local = ki.tag"
						+ " where ki.tag in ("+tags+")"
						+ " and ki.image = "+row.image
						);
				
				String location;
				
				if (resultSet.next()) {
					location = resultSet.getString("name");
				}
				else {
					location = "Unknown";
				}	

				photo = addPhoto(row.baseName,location,row.date);
				out.put(row.image, photo);
				
				resultSet = statement.executeQuery("select *"
						 + " from AgLibraryCollectionImage lci"
						 + " where lci.image = "+row.image
						 + " and lci.collection = ("
						 + "     select id_local"
						 + "     from AgLibraryCollection"
						 + "     where name = 'Favourite Birds'"
						 + " )"
						 );

				if (resultSet.next()) {
					favourites.add(photo);
					uploadPhoto("/media/thomas/Storage/Documents/Photos/Lightroom/Birds/"
					+photo.getPhoto().getPath()+".jpg");
					uploadPhoto("/media/thomas/Storage/Documents/Photos/Lightroom/Birds/"
					+photo.getPhoto().getPath()+"_thumb.jpg");
				}
			}
			
			BirdResource bird = birds.get(row.keyword);
			
			Set<Photo> birdPhotos = bird.getBird().getPhotos();
			
			if (birdPhotos==null) {
				birdPhotos = new HashSet<Photo> ();
			}
			
			birdPhotos.add(photo.getPhoto());
			
			if (favourites.contains(photo)) {
				bird.getBird().setFavourite(photo.getPhoto());
			}
			
			updateBird(bird);
					
		}
		
		return out.values();

	}
	
	private class PhotoRow {
		
		Integer image;
		Integer keyword;
		Date date;
		String baseName;
		String pathFromRoot;
		
		PhotoRow(ResultSet resultSet) throws SQLException {
			image = resultSet.getInt("id_local");
			keyword = resultSet.getInt("tag");
			try {
				date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse(resultSet.getString("captureTime"));
			} catch (ParseException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			baseName = resultSet.getString("baseName");
			pathFromRoot = resultSet.getString("pathFromRoot");
		}
		
		
	}

	
	
	
}

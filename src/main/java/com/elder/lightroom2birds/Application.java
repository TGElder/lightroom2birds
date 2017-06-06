package com.elder.lightroom2birds;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestTemplate;

import com.tgelder.birds.Bird;
import com.tgelder.birds.Photo;

public class Application {

    private static final Logger log = LoggerFactory.getLogger(Application.class);

    public static void main(String args[]) {
        RestTemplate restTemplate = new RestTemplate();
//        Bird bird = restTemplate.getForObject("http://localhost:8080/birds/2", Bird.class);
//        log.info(bird.toString());
        
        Bird bullfinch = new Bird("bullfinch");
        
        URI uri = restTemplate.postForLocation("http://localhost:8080/birds/", bullfinch);
        
        bullfinch = restTemplate.getForObject(uri, Bird.class);
        
        log.info(bullfinch.toString());
        
        Photo photo = restTemplate.getForObject("http://localhost:8080/photos/1", Photo.class);
        
        bullfinch.setFavourite(photo);
        
        restTemplate.put(uri, bullfinch);
    }

}
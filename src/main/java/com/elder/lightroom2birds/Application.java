package com.elder.lightroom2birds;

import java.sql.SQLException;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.tgelder.birds.BirdResource;
import com.tgelder.birds.PhotoResource;

public class Application {
	
    private static final Logger log = LoggerFactory.getLogger(Application.class);

    public static void main(String args[]) throws ClassNotFoundException, SQLException {

        Lightroom2Birds lightroom2birds = new Lightroom2Birds();
        
        //BirdResource bullFinch = lightroom2birds.addBird("bullFinch");
        //PhotoResource jackdaw = lightroom2birds.addPhoto("jackdaw.jpg","Kew Gardens", new Date());
        //lightroom2birds.setFavourite(bullFinch, jackdaw);
        
        System.out.println(lightroom2birds.getBirds());
        
        lightroom2birds.close();
         
    }

}
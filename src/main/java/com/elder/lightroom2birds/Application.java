package com.elder.lightroom2birds;

import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class Application {
	
    private static final Logger log = LoggerFactory.getLogger(Application.class);

    
    public static void main(String args[]) throws ClassNotFoundException, SQLException {

        Lightroom2Birds lightroom2birds = new Lightroom2Birds(args[0],args[1]);

        
        lightroom2birds.getPhotos(lightroom2birds.getBirds());
        
     
        lightroom2birds.close();
         
    }

}
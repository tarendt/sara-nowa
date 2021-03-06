/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package bwfdm.sara.utils;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.ClientErrorException;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.RedirectionException;
import javax.ws.rs.ServerErrorException;
import javax.ws.rs.ServiceUnavailableException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bwfdm.sara.publication.dspace.DSpace_v6;


public class WebUtils {
	
	private static final Logger logger = LoggerFactory.getLogger(DSpace_v6.class);
	
	/**
	 * Get JAX-RS client without SSL Method is got from
	 * {@link https://dzone.com/articles/jersey-ignoring-ssl}
	 * 
	 * @return Client client
	 * @throws Exception
	 * @author vk
	 * 
	 * @deprecated too insecure for use in production code
	 */
	@Deprecated
    public static Client getClientWithoutSSL() {
	    try {
		    SSLContext sslcontext = SSLContext.getInstance("TLS");
		    sslcontext.init(null, new TrustManager[]{new X509TrustManager() {
		        @Override
		        public void checkClientTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {}
		        @Override
		        public void checkServerTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {}
		        @Override
		        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
		
		    }}, new java.security.SecureRandom());
		    return ClientBuilder.newBuilder().sslContext(sslcontext).hostnameVerifier(new HostnameVerifier() {
				@Override
				public boolean verify(String hostname, SSLSession session) {
						return false;
					}
				}).build();
	    } catch (Exception e) {
	    	logger.error("Exception by ignoring SSL varification: " 
					+ e.getClass().getSimpleName() + ": " + e.getMessage());
	    	return null;
	    }
    }
    
    /**
     * Extract response result.
     * An idea is got from "https://github.com/ctu-developers/DSpace-REST-client/blob/master/src/main/java/cz/cvut/dspace/rest/client/impl/AbstractDSpaceRESTClient.java"
     * 
     * @param <T>
     * @param extractType
     * @param response
     * @return 
     */
    public static <T> T readResponseEntity(Class<T> extractType, Response response){
        int status = response.getStatus();
        if (status>=200 && status <300){
            return response.readEntity(extractType);
        } else {
            try {
                handleResponseStatus(response);
            } catch (WebApplicationException e){
            	logger.error("Exception by reading the response entity: " 
    					+ e.getClass().getSimpleName() + ": " + e.getMessage());
            } finally {
                response.close();
            }
        }
        return null;
    }
    
    /**
     * Handle response status, analyze errors.
     * The idea is got from {@link https://github.com/ctu-developers/DSpace-REST-client/blob/master/src/main/java/cz/cvut/dspace/rest/client/impl/AbstractDSpaceRESTClient.java}
     *
     * @param response
     * @throws WebApplicationException 
     */
    public static void handleResponseStatus(Response response) throws WebApplicationException {
        
        final int status = response.getStatus();
        if (status>=200 && status<300){
            return;
        } else if (status == 400){              
            throw new BadRequestException(response);
        } else if (status == 401){
            throw new NotAuthorizedException(response);
        } else if (status == 404) {
            throw new NotFoundException(response);
        } else if (status == 500) {
            throw new InternalServerErrorException(response);
        } else if (status == 503) {
            throw new ServiceUnavailableException(response);
        } else if (status >= 500) {
            throw new ServerErrorException(response);
        } else if (status >= 400) {
            throw new ClientErrorException(response);
        } else if (status >= 300) {
            throw new RedirectionException(response);
        } else {
            throw new WebApplicationException(response);
        }                
    }
    
    
    
            // to remove
        // Encoding, it is done automatically! We do not need it.
//        try {
//            email = URLEncoder.encode(email, "ISO-8859-1");
//            password = URLEncoder.encode(password, "ISO-8859-1");
//            System.out.println("email + password encoded: " + email + " " + password);
//        } catch (UnsupportedEncodingException ex) {
//            Logger.getLogger(OparuSix.class.getName()).log(Level.SEVERE, null, ex);
//        }
    
    
}

package eu.dissco.refineextension.processing;

import net.dona.doip.client.DoipClient;
import net.dona.doip.client.DoipException;
import net.dona.doip.client.PasswordAuthenticationInfo;
import net.dona.doip.client.QueryParams;
import net.dona.doip.client.SearchResults;
import net.dona.doip.client.ServiceInfo;
import net.dona.doip.client.TokenAuthenticationInfo;
import net.dona.doip.client.transport.DoipClientResponse;

import com.google.gson.JsonObject;

import net.dona.doip.InDoipMessage;
import net.dona.doip.client.DigitalObject;

public class NsidrClient {
  /*
    public static String NSIDR_DOIP_SERVICE = "wildlive/service";
    public static String NSIDR_URL = "localhost";
    public static int NSIDR_PORT = 9000;
    */
  public static String NSIDR_DOIP_SERVICE = "20.5000.1025/service";
  public static String NSIDR_URL = "nsidr.org";
  public static int NSIDR_PORT = 9000;

    private DoipClient doipClient;
    private TokenAuthenticationInfo authInfo;
    private ServiceInfo serviceInfo;
    
    public NsidrClient(String token) {
        this.doipClient = new DoipClient();
        // the first parameter clientId can be ignored because this information is contained
        // in the jwt token which will be handled by a custom method in Cordra
        this.authInfo = new TokenAuthenticationInfo("", token); 
        this.serviceInfo = new ServiceInfo(NSIDR_DOIP_SERVICE, NSIDR_URL, NSIDR_PORT);
        try {
          this.hello();
        } catch(DoipException e) {
          System.out.println("A doip exception thrwon in HELLO!");
          System.out.println("Status: " + e.getStatusCode());
          System.out.println("message:" + e.getMessage());
          e.printStackTrace();
        }
    }
    
    public DigitalObject create(DigitalObject newObject) throws DoipException {
        return this.doipClient.create(newObject, this.authInfo, this.serviceInfo);
    }
    
    public DigitalObject update(DigitalObject updateObject) throws DoipException {
        return this.doipClient.update(updateObject, this.authInfo, this.serviceInfo);
    }
    
    public DigitalObject retrieve(String id) throws DoipException {
        return this.doipClient.retrieve(id, this.authInfo, this.serviceInfo);
    }
    
    public SearchResults<DigitalObject> search(String queryString) throws DoipException {
        QueryParams queryParams = new QueryParams(0, 10);
        return this.doipClient.search(NSIDR_DOIP_SERVICE, queryString,
                queryParams, this.authInfo, this.serviceInfo);
    }
    
    public DoipClientResponse performDoipOperationGet(String targetId, String operationId) throws DoipException {
            return this.doipClient.performOperation(targetId,operationId,this.authInfo,null,this.serviceInfo);        
    }
    
    public DigitalObject hello() throws DoipException {
      System.out.println("calling hello");
      // return this.doipClient.hello(NSIDR_DOIP_SERVICE, this.authInfo, this.serviceInfo);
      return this.doipClient.hello(NSIDR_DOIP_SERVICE, new PasswordAuthenticationInfo("admin", "dstest"), this.serviceInfo);
    }
}

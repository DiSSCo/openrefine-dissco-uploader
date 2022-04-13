package eu.dissco.refineextension.processing;

import net.dona.doip.client.DoipClient;
import net.dona.doip.client.DoipException;
import net.dona.doip.client.PasswordAuthenticationInfo;
import net.dona.doip.client.QueryParams;
import net.dona.doip.client.ServiceInfo;
import net.dona.doip.client.TokenAuthenticationInfo;
import net.dona.doip.client.transport.DoipClientResponse;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.dona.doip.InDoipMessage;
import net.dona.doip.client.DigitalObject;

import net.cnri.cordra.api.CordraException;
import net.cnri.cordra.api.CordraObject;
import net.cnri.cordra.api.SearchResults;
import net.cnri.cordra.api.Options;
import net.cnri.cordra.api.TokenUsingHttpCordraClient;

public class CordraClient {
  public String cordraUrl;
  private String authToken;
  private TokenUsingHttpCordraClient nsidrRestClient;

  public CordraClient(String cordraUrl, String token) {
    try {
      // the first parameters username and password be ignored because this information is contained
      // in the jwt token which will be handled by a custom method in Cordra
      this.cordraUrl = cordraUrl;
      this.nsidrRestClient = new TokenUsingHttpCordraClient(this.cordraUrl, "", "");
    } catch (CordraException e) {
      System.out.println("cordra exception" + e.getMessage());
      e.printStackTrace();
    }
    this.authToken = token;
  }

  public CordraObject create(CordraObject newObject) throws CordraException {
    Options options = new Options();
    options.setAuthHeader("Bearer " + authToken);
    return this.nsidrRestClient.create(newObject, options);
  }

  public CordraObject update(CordraObject updateObject) throws CordraException {
    Options options = new Options();
    options.setAuthHeader("Bearer " + authToken);
    return this.nsidrRestClient.update(updateObject, options);
  }


  public CordraObject get(String id) throws CordraException {
    Options options = new Options();
    options.setAuthHeader("Bearer " + authToken);
    return this.nsidrRestClient.get(id, options);
  }

  public SearchResults<CordraObject> search(String queryString) throws CordraException {
    Options options = new Options();
    options.setAuthHeader("Bearer " + authToken);
    return this.nsidrRestClient.search(queryString, options);
  }

  public JsonElement performDoipOperationGetRest(String targetId, String operationId)
      throws CordraException {
    Options options = new Options();
    options.setAuthHeader("Bearer " + authToken);
    return this.nsidrRestClient.call(targetId, operationId, new JsonObject(), options);
  }

}

package eu.dissco.refineextension.processing;

import java.io.IOException;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.refine.util.HttpClient;
import org.apache.hc.client5.http.ClientProtocolException;

import eu.dissco.refineextension.schema.DigitalObject;

public class SpecimenGetClient {
  private HttpClient httpClient;
  private String disscoSpecimenGetEndpoint;
  
  public SpecimenGetClient(String authToken, String disscoSpecimenGetEndpoint) {
    //NOTE authToken currently unused
    this.httpClient = new HttpClient();
    this.disscoSpecimenGetEndpoint = disscoSpecimenGetEndpoint;
  }
  
  public DigitalObject get(String id) throws IOException {
    String urlString = disscoSpecimenGetEndpoint + "/" + id;
    HttpClientResponseHandler<String> responseHandler = getResponseHandler(urlString);
    String responseStr = this.httpClient.getResponse(urlString, null, responseHandler);
    JsonObject response = new Gson().fromJson(responseStr, JsonObject.class);
    if(response.has("@id") && response.has("@type")) {
      DigitalObject createdObject = new DigitalObject(response.get("@type").getAsString(), response);
      createdObject.id = response.get("@id").getAsString();
      return createdObject;
      }
    return null;
  }

  private HttpClientResponseHandler<String> getResponseHandler(String urlString){
     return new HttpClientResponseHandler<String>() {
      @Override
      public String handleResponse(final ClassicHttpResponse response) throws IOException {
          final int status = response.getCode();
          if (status >= HttpStatus.SC_SUCCESS && status < HttpStatus.SC_REDIRECTION) {
              final HttpEntity entity = response.getEntity();
              if (entity == null) {
                  throw new IOException("No content found in " + urlString);
              }
              try {
                  return EntityUtils.toString(entity);
              } catch (final ParseException ex) {
                  throw new ClientProtocolException(ex);
              }
          } else {
              // String errorBody = EntityUtils.toString(response.getEntity());
              throw new ClientProtocolException(String.format("HTTP error %d : %s for URL %s", status,
                      response.getReasonPhrase(), urlString));
          }
      }
  };
  }
}
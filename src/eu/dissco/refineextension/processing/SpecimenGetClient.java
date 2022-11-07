package eu.dissco.refineextension.processing;

import java.io.IOException;
import java.util.Iterator;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.refine.util.HttpClient;
import org.apache.hc.client5.http.ClientProtocolException;

import eu.dissco.refineextension.schema.DigitalObject;

public class SpecimenGetClient {
  private HttpClient httpClient;
  private String disscoSpecimenGetEndpoint;

  public SpecimenGetClient(String authToken, String disscoSpecimenGetEndpoint) {
    // NOTE authToken currently unused
    this.httpClient = new HttpClient();
    this.disscoSpecimenGetEndpoint = disscoSpecimenGetEndpoint;
  }

  public DigitalObject get(String id) throws IOException {
    String urlString = disscoSpecimenGetEndpoint + "/" + id + "/full";
    System.out.println("will get: " + urlString);
    HttpClientResponseHandler<String> responseHandler = getResponseHandler(urlString);
    String responseStr = this.httpClient.getResponse(urlString, null, responseHandler);
    JsonObject response = new Gson().fromJson(responseStr, JsonObject.class);
    System.out.println(response.toString());
    if (response.has("digitalSpecimen")) {
      JsonObject digitalSpecimen = response.getAsJsonObject("digitalSpecimen");
      digitalSpecimen.remove("dwcaId");
      //digitalSpecimen.remove("version");
      //digitalSpecimen.remove("created");
      if (digitalSpecimen.has("id") && digitalSpecimen.has("type")) {
        DigitalObject createdObject =
            new DigitalObject(digitalSpecimen.get("type").getAsString(), digitalSpecimen);
        createdObject.id = digitalSpecimen.get("id").getAsString();
        if(response.has("digitalMediaObjects")) {
          JsonArray digitalMediaObjects = new JsonArray();
          Iterator<JsonElement> iter = response.get("digitalMediaObjects").getAsJsonArray().iterator();
          while(iter.hasNext()) {
            JsonObject dmo = iter.next().getAsJsonObject();
            if(dmo.has("digitalMediaObject")) {
              JsonObject digitalMediaObject = dmo.getAsJsonObject("digitalMediaObject");
              //digitalMediaObject.remove("version");
              //digitalMediaObject.remove("created");
              digitalMediaObjects.add(digitalMediaObject);
            }
          }
          digitalSpecimen.add("digitalMediaObjects", digitalMediaObjects);
        }
        return createdObject;
      }
    }
    return null;
  }

  private HttpClientResponseHandler<String> getResponseHandler(String urlString) {
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

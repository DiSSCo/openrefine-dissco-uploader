package eu.dissco.refineextension.processing;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpConnection;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.impl.Http1StreamListener;
import org.apache.hc.core5.http.impl.bootstrap.HttpRequester;
import org.apache.hc.core5.http.impl.bootstrap.RequesterBootstrap;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.HttpEntities;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.hc.core5.http.message.RequestLine;
import org.apache.hc.core5.http.message.StatusLine;
import org.apache.hc.core5.http.protocol.HttpCoreContext;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.message.MessageWriter;
import io.cloudevents.core.v1.CloudEventBuilder;
import io.cloudevents.http.HttpMessageFactory;

import eu.dissco.refineextension.schema.DigitalObject;

public class SpecimenPostClient {
  protected String disscoSpecimenPostEndpoint =
      "https://sandbox.dissco.tech/processing/digitalspecimen";
  private String authToken;

  public SpecimenPostClient(String token) {
    System.out.println(
        "Initializing DisscoSpecimenPostClient with endpoint " + disscoSpecimenPostEndpoint);
    this.authToken = token;
  }

  public DigitalObject upsert(DigitalObject newObject) {
    System.out.println("create called new with endpoint " + disscoSpecimenPostEndpoint);
    System.out.println("newObject has id: ");
    System.out.println(newObject.id);

    // newObject.content.addProperty("@type", newObject.type);
    String type = newObject.type;
    newObject.content.addProperty("type", newObject.type);

    if(newObject.content.has("digitalMediaObjects")) {
      newObject.content.remove("digitalMediaObjects");
    }
    
    JsonObject data = new JsonObject();
    HttpPost request;
    
    Set<String> specimenTypes = Set.of("BotanySpecimen", "ZoologyVertebrateSpecimen");
    Set<String> mediaTypes = Set.of("2DImageObject", "3DImageObject");
    if(specimenTypes.contains(type)) {
      data.add("digitalSpecimen", newObject.content);
      request = new HttpPost(this.disscoSpecimenPostEndpoint);
    } else if(mediaTypes.contains(type)) {
      data.add("digitalMediaObject", newObject.content);
      request = new HttpPost("https://sandbox.dissco.tech/processing/digitalmedia");
    } else {
      System.out.println("Unknown type to create: " + type);
      return null;
    }
    //JsonObject enrichmentEntry = new JsonObject();
    // enrichmentEntry.addProperty("name", "plant-organ-detection");
    // enrichmentEntry.addProperty("imageOnly", true);
    JsonArray enrichment = new JsonArray();
    // TODO: configure enchrichments via ui
    //enrichment.add(enrichmentEntry);
    data.add("enrichmentList", enrichment);
    
    request.setHeader("Authorization", "Bearer " + authToken);
    
    try {
      System.out.println("requesting: " + request.getUri());
      System.out.println(request.getFirstHeader("Authorization"));
    } catch (URISyntaxException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    request.setEntity(HttpEntities.create(data.toString(), ContentType.APPLICATION_JSON));
    System.out.println(request.toString());
    System.out.println(data.toString());
    // try (ClassicHttpResponse response = httpRequester.ex .execute(target, request,
    // Timeout.ofSeconds(5), coreContext)) {
    try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
      try (CloseableHttpResponse response = httpclient.execute(request)) {
        System.out.println("Got returned code:" + response.getCode());
        HttpEntity entity = response.getEntity();
        String result = EntityUtils.toString(entity);
        System.out.println("result: " + result);
        // Ensure that the stream is fully consumed
        EntityUtils.consume(entity);
        System.out.println("==============");
        System.out.println("No changes were necessary to specimen with id");
        System.out.println(result.substring(0, 45));
        if(!result.substring(0, 45).equals("No changes were necessary to specimen with id")) {
          JsonObject body = new JsonParser().parse(result).getAsJsonObject();
          if (body.has("id")) {
            // createdObject = new DigitalObject(body.get("@type").getAsString(), body);
            newObject.id = body.get("id").getAsString();
          }
        }
      }
    } catch (IOException | ParseException e1) {
      // TODO Auto-generated catch block
      e1.printStackTrace();
    }
    if(newObject.id != null) {
      return newObject;
    } else {
      return null;
    }
  }
}

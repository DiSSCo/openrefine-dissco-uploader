package eu.dissco.refineextension.processing;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.message.MessageWriter;
import io.cloudevents.core.v1.CloudEventBuilder;
import io.cloudevents.http.HttpMessageFactory;
import net.cnri.cordra.api.CordraException;
import net.cnri.cordra.api.CordraObject;

public class DisscoSpecimenPostClient {
  protected String disscoSpecimenPostEndpoint = "https://sandbox.dissco.tech/opends";
  private String authToken;

  public DisscoSpecimenPostClient(String token) {
    System.out.println(
        "Initializing DisscoSpecimenPostClient with endpoint " + disscoSpecimenPostEndpoint);
    this.authToken = token;
  }

  public CordraObject create(JsonObject newObject) {
    System.out.println("create with endpoint " + disscoSpecimenPostEndpoint);
    System.out.println("create was called with token: " + this.authToken);

    JsonObject data = new JsonObject();
    data.add("openDS", newObject.get("content"));
    JsonObject enrichmentEntry = new JsonObject();
    enrichmentEntry.addProperty("name", "plant-organ-detection");
    enrichmentEntry.addProperty("imageOnly", true);
    JsonArray enrichment = new JsonArray();
    // TODO: configure enchrichments via ui
    enrichment.add(enrichmentEntry);
    data.add("enrichment", enrichment);

    System.out.println("will sent CloudEvent:");
    System.out.println(data.toString());

    CloudEvent ceToSend = new CloudEventBuilder().withId("my-id")
        .withSource(URI.create("https://address-open-refine.dissco.eu"))
        .withType("eu.dissco.translator.event").withDataContentType("application/json")
        .withData(data.toString().getBytes(StandardCharsets.UTF_8)).build();

    CordraObject createdObject = null;

    try {
      URL url = new URL(this.disscoSpecimenPostEndpoint);
      HttpURLConnection httpUrlConnection = (HttpURLConnection) url.openConnection();
      httpUrlConnection.setRequestMethod("POST");
      httpUrlConnection.setDoOutput(true);
      httpUrlConnection.setDoInput(true);

      System.out.println("created event: " + ceToSend.toString());

      MessageWriter messageWriter = createMessageWriter(httpUrlConnection, this.authToken);
      messageWriter.writeBinary(ceToSend);


      // read the returned message
      Map<String, List<String>> headers = httpUrlConnection.getHeaderFields();
      System.out.println("header fields returned: ");
      System.out.println(headers.toString());

      try (Scanner scanner = new Scanner(httpUrlConnection.getInputStream())) {
        String responseBody = scanner.useDelimiter("\\A").next();
        System.out.println(responseBody);
        JsonObject body = JsonParser.parseString(responseBody).getAsJsonObject();
        if (body.has("@id")) {
          createdObject = new CordraObject(body.get("@type").getAsString(), body);
          createdObject.id = body.get("@id").getAsString();
        }
      }
    } catch (IOException e) {
      System.out.println("IOException");
      e.printStackTrace();
    }
    return createdObject;
  }

  private static MessageWriter createMessageWriter(HttpURLConnection httpUrlConnection,
      String token) {
    return HttpMessageFactory.createWriter(httpUrlConnection::setRequestProperty, body -> {
      try {
        if (body != null) {
          httpUrlConnection.setRequestProperty("content-length", String.valueOf(body.length));
          httpUrlConnection.setRequestProperty("authorization", "Bearer " + token);
          try (OutputStream outputStream = httpUrlConnection.getOutputStream()) {
            outputStream.write(body);
          }
        } else {
          httpUrlConnection.setRequestProperty("content-length", "0");
        }
      } catch (IOException t) {
        throw new UncheckedIOException(t);
      }
    });
  }
}

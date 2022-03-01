
package eu.dissco.refineextension.processing;

import java.io.IOException;
import java.util.Iterator;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.diff.JsonDiff;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.cnri.cordra.api.CordraException;
import net.cnri.cordra.api.CordraObject;
import net.cnri.cordra.api.SearchResults;

public class DigitalSpecimenProcessor {

  private NsidrClient nsidrClient;

  public DigitalSpecimenProcessor(String authToken) {
    this.nsidrClient = new NsidrClient(authToken);
  }

  private JsonArray getMediaObjects(JsonObject dsContent) {
    JsonElement mediaCollectionEl = dsContent.get("ods:mediaCollection");
    if (mediaCollectionEl != null && mediaCollectionEl.isJsonObject()) {
      JsonElement mediaObjectsEl = mediaCollectionEl.getAsJsonObject().get("ods:mediaObjects");
      if (mediaObjectsEl != null && mediaObjectsEl.isJsonArray()) {
        return mediaObjectsEl.getAsJsonArray();
      }
    }
    return null;
  }

  public JsonNode getDigitalSpecimenDataDiff(CordraObject dsRemote,
      JsonObject dsLocalContentToUpload) throws CordraException, IOException {


    JsonObject dsContentRemote = dsRemote.content.getAsJsonObject();
    ObjectMapper mapper = new ObjectMapper();
    // 1. Check if there are mediaObjects in the upload content
    JsonArray mediaObjectsLocal = this.getMediaObjects(dsLocalContentToUpload);
    if (mediaObjectsLocal.size() > 0) {
      // If yes, we need to deserialize the dsContentRemote in order to
      // compare the media content
      JsonElement mediaCollectionElRemote = dsContentRemote.get("ods:mediaCollection");
      if (mediaCollectionElRemote != null && mediaCollectionElRemote.isJsonPrimitive()) {
        String mediaCollectionId = mediaCollectionElRemote.getAsString();
        if (mediaCollectionId.length() > 0) {
          try {
            JsonElement response =
                this.nsidrClient.performDoipOperationGetRest(dsRemote.id, "getDeserialized");
            JsonObject dsRemoteDeserialized = response.getAsJsonObject();
            dsContentRemote = dsRemoteDeserialized.get("content").getAsJsonObject();
          } catch (CordraException e) {
            e.printStackTrace();
          }

        } else {
          // make a custom message that a new mediacollection item
          // will be created
          String jsonPatches = "[{";
        }
      } else {
        // To-do handle error
      }
    }
    // we need to convert gson JsonObject to jackson JsonNode
    // the resulting diffs will be a patch to the remote content (in order
    // to be the same as the upload content)
    JsonNode target = mapper.readTree(dsLocalContentToUpload.toString());
    JsonNode source = mapper.readTree(dsContentRemote.toString());
    final JsonNode patchNode = JsonDiff.asJson(source, target);
    if (patchNode.size() > 0) {
      System.out.println("Found json diff:");
      System.out.println(patchNode.toString());
    }
    return patchNode;
  }

  private CordraObject createMediaCollection(JsonObject mediaCollectionContent, String DSID)
      throws CordraException {
    // First: Create an ID (local scope) for every MediaObject
    JsonArray mediaObjects = mediaCollectionContent.get("ods:mediaObjects").getAsJsonArray();
    Iterator<JsonElement> iter = mediaObjects.iterator();
    while (iter.hasNext()) {
      JsonObject mediaObject = iter.next().getAsJsonObject();
      // this creates a 36 digit unique ID
      String uid = UUID.randomUUID().toString();
      // we need only a short ID because we have a very narrow scope
      String shortId = uid.substring(0, 10);
      // To-Do: It should be made sure that the ID does not exist in
      // the same mediaCollection
      // (even though probability is very low)
      mediaObject.addProperty("ods:mediaId", shortId);
    }

    mediaCollectionContent.addProperty("ods:digitalSpecimen", DSID);
    mediaCollectionContent.add("ods:mediaObjects", mediaObjects);
    CordraObject mediaCollectionToCreate = new CordraObject();
    mediaCollectionToCreate.type = "MediaCollectionV0.1";
    mediaCollectionToCreate.setContent(mediaCollectionContent);
    return this.nsidrClient.create(mediaCollectionToCreate);
  }


  public CordraObject createDigitalSpecimen(CordraObject ds) throws CordraException {
    JsonObject content = ds.content.getAsJsonObject();
    JsonArray mediaObjects = this.getMediaObjects(content);
    CordraObject createdDs = this.nsidrClient.create(ds);
    if (mediaObjects.size() > 0) {
      // then there are media objects so a MediaCollection object must be
      // created

      JsonObject mediaCollectionContent = content.remove("ods:mediaCollection").getAsJsonObject();

      CordraObject newMediaCollection =
          this.createMediaCollection(mediaCollectionContent, createdDs.id);

      // now overwrite the mediaCollection attribute with the ID
      // of the created DO
      JsonObject createdContent = createdDs.content.getAsJsonObject();
      createdContent.addProperty("ods:mediaCollection", newMediaCollection.id);
      createdDs = this.nsidrClient.update(createdDs);
      // internally for the OpenRefine frontend overwrite the mediaCollection attribute with the
      // content of the other DO
      createdDs.content.getAsJsonObject().add("ods:mediaCollection", newMediaCollection.content);
    }
    return createdDs;
  }

  public CordraObject findRemoteDigitalSpecimenById(String id) {
    CordraObject ds = null;
    if (id.length() > 0) {
      try {
        ds = this.nsidrClient.get(id);
      } catch (CordraException e) {
        System.out
            .println("an CordraObjectRepositoryException was thrown in retrieve" + e.getMessage());
        e.printStackTrace();
      }
    }
    return ds;
  }

  public CordraObject findRemoteDigitalSpecimenByCuratedObjectID(String curatedObjectID) {
    CordraObject ds = null;
    if (curatedObjectID.length() > 0) {
      try {
        String queryString =
            "/ods\\:authoritative/ods\\:curatedObjectID:\"" + curatedObjectID + "\"";
        SearchResults<CordraObject> results = this.nsidrClient.search(queryString);
        if (results.size() == 1) {
          ds = results.iterator().next();
        }
        results.close();
        // To-Do: handle case if more than 1
      } catch (CordraException e) {
        System.out
            .println("an CordraObjectRepositoryException was thrown in search" + e.getMessage());
        e.printStackTrace();
      }
    }
    return ds;
  }

  private CordraObject updateMediaCollection(CordraObject md, JsonObject newContent)
      throws CordraException {
    md.setContent(newContent);
    return this.nsidrClient.update(md);
  }

  public CordraObject updateDigitalSpecimen(CordraObject ds, JsonNode patch)
      throws CordraException {
    // first thing to check: are there mediaObjects in the local dsObject
    JsonObject contentToUpload = ds.content.getAsJsonObject();
    JsonArray mediaObjects = this.getMediaObjects(contentToUpload);
    if (mediaObjects.size() > 0) {
      // iterate the scheduled changes in the patch and see if any affects
      // the media section
      boolean mediaChanged = false;
      Iterator<JsonNode> iter = patch.elements();
      while (iter.hasNext()) {
        JsonNode change = iter.next();
        if (change.has("path")) {
          JsonNode pathEl = change.get("path");
          if (pathEl.isTextual()) {
            String path = pathEl.asText();
            if (path.startsWith("/ods:mediaCollection")) {
              mediaChanged = true;
              break;
            }
          }
        }
      }

      CordraObject updatedMediaObject = null;
      // check if there is an existing MediaCollection object in the
      // remote DS
      // To-Do: in the future this might be included in the
      // deserialization so no need to
      // fetch the remote DS again
      CordraObject remoteDS = this.retrieve(ds.id);
      JsonObject contentRemote = remoteDS.content.getAsJsonObject();
      if (contentRemote.has("ods:mediaCollection")) {
        JsonElement mediaCollectionEl = contentRemote.get("ods:mediaCollection");
        if (mediaCollectionEl.isJsonPrimitive()) {
          String mediaCollectionId = mediaCollectionEl.getAsString();
          CordraObject remoteMediaCollection = this.retrieve(mediaCollectionId);
          updatedMediaObject = remoteMediaCollection;

          if (mediaChanged) {

            updatedMediaObject = this.updateMediaCollection(remoteMediaCollection,
                contentToUpload.get("ods:mediaCollection").getAsJsonObject());
          }
        } else {
          JsonObject mediaCollectionContent =
              contentToUpload.remove("ods:mediaCollection").getAsJsonObject();
          updatedMediaObject = this.createMediaCollection(mediaCollectionContent, ds.id);
        }
        contentToUpload.addProperty("ods:mediaCollection", updatedMediaObject.id);
      }
      // now overwrite the mediaCollection attribute with the ID
      // of the created DO
    } else {
      // no mediaObjects could also mean that existing ones have been
      // deleted
      // To-Do: How to handle this? Check for mediaCollectionId -> delete
      // DO?
    }
    return this.nsidrClient.update(ds);
  }

  public CordraObject retrieve(String id) throws CordraException {
    return this.nsidrClient.get(id);
  }

  public SearchResults<CordraObject> search(String queryString) throws CordraException {
    return this.nsidrClient.search(queryString);
  }
}

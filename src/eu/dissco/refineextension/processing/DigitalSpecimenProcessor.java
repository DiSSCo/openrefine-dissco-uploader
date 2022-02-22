
package eu.dissco.refineextension.processing;

import java.io.IOException;
import java.util.Iterator;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.diff.JsonDiff;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import eu.dissco.refineextension.model.SyncState;
import eu.dissco.refineextension.util.DigitalSpecimenUtil;
import net.dona.doip.DoipConstants;
import net.dona.doip.InDoipMessage;
import net.dona.doip.InDoipSegment;
import net.dona.doip.client.DigitalObject;
import net.dona.doip.client.DoipException;
import net.dona.doip.client.SearchResults;
import net.dona.doip.client.transport.DoipClientResponse;

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

    public JsonNode getDigitalSpecimenDataDiff(DigitalObject dsRemote, JsonObject dsLocalContentToUpload)
            throws DoipException, IOException {
        System.out.println("wgetDigitalSpecimenDataDiff caaled!!!");

        ObjectMapper mapper = new ObjectMapper();
        // 1. Check if there are mediaObjects in the upload content
        JsonArray mediaObjectsLocal = this.getMediaObjects(dsLocalContentToUpload);
        JsonObject dsContentRemote = dsRemote.attributes.get("content").getAsJsonObject();
        if (mediaObjectsLocal.size() > 0) {
            System.out.println("size es over 0");
            // If yes, we need to deserialize the dsContentRemote in order to
            // compare the media content
            JsonElement mediaCollectionElRemote = dsContentRemote.get("ods:mediaCollection");
            if (mediaCollectionElRemote != null && mediaCollectionElRemote.isJsonPrimitive()) {
                System.out.println("mediaCollectionElRemote");
                String mediaCollectionId = mediaCollectionElRemote.getAsString();
                if (mediaCollectionId.length() > 0) {
                    System.out.println("mlength over te");
                    DoipClientResponse response = this.nsidrClient.performDoipOperationGet(dsRemote.id,
                            "getDeserialized");
                    System.out.println("we have got the operation response!!! status: " + response.getStatus()
                            + " it is okay: " + String.valueOf(response.getStatus().equals(DoipConstants.STATUS_OK)));
                    System.out.println(response.getAttributes());
                    if (!response.getStatus().equals(DoipConstants.STATUS_OK)) {
                        System.out.println("error when fetching deserialized");
                    } else {
                        System.out.println("else!");
                        InDoipMessage output = response.getOutput();
                        for (InDoipSegment segment : output) {
                            System.out.println("for segment!");
                            System.out.println(segment);
                            if (segment.isJson()) {
                                JsonElement outputJson = segment.getJson();
                                if (outputJson != null && outputJson.isJsonObject()) {
                                    JsonObject dsRemoteDeserialized = outputJson.getAsJsonObject();
                                    // overwrite the content for comparison
                                    dsContentRemote = dsRemoteDeserialized.get("content").getAsJsonObject();
                                }
                            }
                        }
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

    private DigitalObject createMediaCollection(JsonObject digitalSpecimenContent)
            throws DoipException {
        JsonArray mediaObjects = this.getMediaObjects(digitalSpecimenContent);
        // First: Create an ID (local scope) for every MediaObject
        Iterator<JsonElement> iter = mediaObjects.iterator();
        while (iter.hasNext()) {
            JsonObject mediaObject = iter.next().getAsJsonObject();
            // this creates a 36 digit unique ID
            String uid = UUID.randomUUID().toString();
            // we need only a short ID because we have a very narrow scope
            String shortId = uid.substring(0, 10);
            System.out.println("created uid: " + shortId);
            // To-Do: It should be made sure that the ID does not exist in
            // the same mediaCollection
            // (even though probability is very low)
            mediaObject.addProperty("ods:mediaId", shortId);
        }

        JsonElement mediaCollectionEl = digitalSpecimenContent.get("ods:mediaCollection");
        DigitalObject mediaCollectionToCreate = new DigitalObject();
        mediaCollectionToCreate.type = "MediaCollectionV0.1";
        mediaCollectionToCreate.setAttribute("content", mediaCollectionEl);
        return this.nsidrClient.create(mediaCollectionToCreate);
    }

    public DigitalObject createDigitalSpecimen(DigitalObject ds)
            throws DoipException {
        JsonObject content = ds.attributes.get("content").getAsJsonObject();
        JsonArray mediaObjects = this.getMediaObjects(content);
        DigitalObject newMediaCollection = null;
        if (mediaObjects.size() > 0) {
            // then there are media objects so a MediaCollection object must be
            // created

            newMediaCollection = this.createMediaCollection(content);

            System.out.println("MediaCollection upload succes, id. " + newMediaCollection.id);
            // now overwrite the mediaCollection attribute with the ID
            // of the created DO
            content.addProperty("ods:mediaCollection", newMediaCollection.id);
        }
        DigitalObject createdDs = this.nsidrClient.create(ds);
        if (newMediaCollection != null) {
            JsonObject createdDsContent = createdDs.attributes.get("content").getAsJsonObject();
            createdDsContent.add("ods:mediaCollection", newMediaCollection.attributes.get("content"));
        }
        return createdDs;
    }

    public DigitalObject findRemoteDigitalSpecimenById(String id) {
        DigitalObject ds = null;
        if (id.length() > 0) {
            try {
                ds = this.nsidrClient.retrieve(id);
            } catch (DoipException e) {
                System.out.println("an DigitalObjectRepositoryException was thrown!");
                e.printStackTrace();
            }
        }
        return ds;
    }

    public DigitalObject findRemoteDigitalSpecimenByCuratedObjectID(String curatedObjectID) {
        DigitalObject ds = null;
        if (curatedObjectID.length() > 0) {
            try {
                String queryString = "/ods\\:authoritative/ods\\:curatedObjectID:\"" + curatedObjectID + "\"";
                SearchResults<DigitalObject> results = this.nsidrClient.search(queryString);
                if (results.size() == 1) {
                    System.out.println("result search size is 1!!");
                    ds = results.iterator().next();
                    System.out.println(ds);
                }
                // To-Do: handle case if more than 1
            } catch (DoipException e) {
                System.out.println("an DigitalObjectRepositoryException was thrown!");
                e.printStackTrace();
            }
        }
        return ds;
    }

    private DigitalObject updateMediaCollection(DigitalObject md, JsonObject newContent)
            throws DoipException {
        md.setAttribute("content", newContent);
        return this.nsidrClient.update(md);
    }

    public DigitalObject updateDigitalSpecimen(DigitalObject ds, JsonNode patch)
            throws DoipException {
        // first thing to check: are there mediaObjects in the local dsObject
        JsonObject contentToUpload = ds.attributes.get("content").getAsJsonObject();
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

            DigitalObject updatedMediaObject = null;
            // check if there is an existing MediaCollection object in the
            // remote DS
            // To-Do: in the future this might be included in the
            // deserialization so no need to
            // fetch the remote DS again
            DigitalObject remoteDS = this.retrieve(ds.id);
            JsonObject contentRemote = ds.attributes.get("content").getAsJsonObject();
            if (contentRemote.has("ods:mediaCollection")) {
                JsonElement mediaCollectionEl = contentRemote.get("ods:mediaCollection");
                if (mediaCollectionEl.isJsonPrimitive()) {
                    String mediaCollectionId = mediaCollectionEl.getAsString();
                    DigitalObject remoteMediaCollection = this.retrieve(mediaCollectionId);
                    updatedMediaObject = remoteMediaCollection;

                    if (mediaChanged) {

                        updatedMediaObject = this.updateMediaCollection(remoteMediaCollection,
                                contentToUpload.get("ods:mediaCollection").getAsJsonObject());
                    }
                } else {
                    updatedMediaObject = this.createMediaCollection(contentToUpload);
                }
                System.out.println("yyyyyyyy after will:" + updatedMediaObject.id);
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

    public DigitalObject retrieve(String id)
            throws DoipException {
        return this.nsidrClient.retrieve(id);
    }

    public SearchResults<DigitalObject> search(String queryString)
            throws DoipException {
        return this.nsidrClient.search(queryString);
    }
}

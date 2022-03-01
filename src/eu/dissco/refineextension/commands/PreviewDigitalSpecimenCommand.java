package eu.dissco.refineextension.commands;

import com.fasterxml.jackson.databind.JsonNode;

import com.google.refine.browsing.Engine;
import com.google.refine.commands.Command;
import com.google.refine.model.Project;
import com.google.refine.model.Row;

import com.google.refine.browsing.RowVisitor;
import com.google.refine.browsing.FilteredRows;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;

import com.google.refine.util.ParsingUtilities;

import com.google.gson.JsonObject;
import eu.dissco.refineextension.util.DigitalSpecimenUtil;

public class PreviewDigitalSpecimenCommand extends Command {

  @Override
  public void doPost(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    // To-Do: make csrf token check

    Project project = getProject(request);
    try {
      Engine engine = getEngine(request, project);
      String columnMappingString = request.getParameter("columnMapping");
      if (columnMappingString == null) {
        respond(response, "{ \"code\" : \"error\", \"message\" : \"No columnMapping specified\" }");
        return;
      }
      JsonNode columnMapping = ParsingUtilities.evaluateJsonStringToObjectNode(columnMappingString);
      String limitString = request.getParameter("limit");
      int limit;
      try {
        limit = Integer.parseInt(limitString);
      } catch (NumberFormatException e) {
        limit = 5;
      }
      List<JsonObject> resultArray = new ArrayList<JsonObject>();
      FilteredRows filteredRows = engine.getAllFilteredRows();
      filteredRows.accept(project, new MyRowVisitor(resultArray, columnMapping, limit));

      response.setCharacterEncoding("UTF-8");
      response.setHeader("Content-Type", "application/json");
      respond(response, resultArray.toString());
    } catch (Exception e) {
      respondException(response, e);
    }
  }

  protected class MyRowVisitor implements RowVisitor {

    private List<JsonObject> storedObjects;
    private JsonNode columnMapping;
    private int limit = 5;
    private int count = 0;

    public MyRowVisitor(List<JsonObject> s, JsonNode columnMapping, int limit) {
      this.storedObjects = s;
      this.columnMapping = columnMapping;
      this.limit = limit;
    }

    @Override
    public void start(Project project) {
      ;
    }

    @Override
    public boolean visit(Project project, int rowIndex, Row row) {
      if (this.count >= this.limit) {
        return true;
      }
      this.count += 1;
      JsonObject dsObject = DigitalSpecimenUtil.rowToJsonObject(row, this.columnMapping);
      this.storedObjects.add(dsObject);
      return false;
    }

    @Override
    public void end(Project project) {
      ;
    }
  }
}

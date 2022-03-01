const OdsSchemaAlignmentDialog = {};
let columnMapping;
const defaultColumnMapping = {
	doi: null,
	"ods:authoritative": {
		"ods:curatedObjectID": null,
		"ods:midsLevel": null,
		"ods:name": null,
		"ods:institution": null,
		"ods:institutionCode": null,
		"ods:materialType": null
	},
	"ods:mediaCollection": {
		"ods:mediaObjects": []
	},
	"ods:extended": {

	}
};
const mediaObjectMapping = {
	"mediatype": null,
	"ods:mediaId": null,
	"ods:mediaUrl": null,
	"ods:imageWidth": null,
	"ods:imageHeight": null,
	"ods:imageSizeUnit": null
}


function createTableHtmlForCol(attributeName, pathAsArray, columns, storedIndex) {
	let html = "";
	let jsonPathString = "";
	let indent = 0;
	for (let i = 0; i < pathAsArray.length; i++) {
		jsonPathString += '[\'' + pathAsArray[i] + '\']'
		indent += 5;
	}
	html += '<tr><td style="padding-left:' + indent + 'px">' + attributeName + '</td>';
	html += '<td><select onclick="{columnMapping' + jsonPathString;

	html += '[\'' + attributeName + '\'] = parseInt(this.value);}">';

	html += '<option value="">--Please choose an option--</option>';

	for (let j = 0; j < columns.length; j++) {
		html += '<option ';
		const idx = columns[j].cellIndex;
		if (storedIndex !== undefined && storedIndex === idx) {
			html += 'selected ';
		}
		html += 'value="' + idx + '">' + columns[j].name + '</option>';
	}
	html += "</select></td><td></td></tr>";
	return html;
}

function serializeSchemaRecursive(schemaObjectPart, pathAsArray, columns) {
	let html = "";
	const objectKeys = Object.keys(schemaObjectPart);
	// To-do: This currently does not allow further nesting of objects
	for (let i = 0; i < objectKeys.length; i++) {
		const key = objectKeys[i];
		const value = schemaObjectPart[key];
		if (typeof value === "object" && value !== null) {
			// then it is a subsection
			let indent = 0;
			for (let i = 0; i < pathAsArray.length; i++) {
				indent += 5;
			}
			html += '<tr><th colspan="2" style="padding-left:' + indent + 'px">' + key + '</th><td>'
			if (key === "ods:mediaObjects") {
				html += '<button class="button" onclick="columnMapping[\'ods:mediaCollection\'][\'ods:mediaObjects\'].push({...mediaObjectMapping});OdsSchemaAlignmentDialog.buildTableHtml(false);">Add object</button>';
			}
			html += '</td></tr>';
			const pathAsArrayClone = [...pathAsArray];
			pathAsArrayClone.push(key);
			html += serializeSchemaRecursive(value, pathAsArrayClone, columns);
		} else {
			// then it is a schema mapping term
			const storedIndex = value;
			html += createTableHtmlForCol(key, pathAsArray, columns, storedIndex);
		}
	}
	return html;
}

OdsSchemaAlignmentDialog.initColumnMapping = function(reset = false) {
	const savedSchema = theProject.overlayModels.disscoSchema;
	if (savedSchema && savedSchema.columnMapping && !reset) {
		columnMapping = savedSchema.columnMapping;
	} else {
		columnMapping = defaultColumnMapping;
	}
}

OdsSchemaAlignmentDialog.buildTableHtml = function() {
	const columns = theProject.columnModel.columns;
	const tableHtml = serializeSchemaRecursive(columnMapping, [], columns)
	$("#ods-mapping-tabs-schema>div>table").empty().append(tableHtml);
}

OdsSchemaAlignmentDialog.launch = function() {
	const frame = $(DOM.loadHTML("nsidr", "scripts/dialogs/ods-schema-alignment-dialog.html"));
	const elmts = this.elmts = DOM.bind(frame);

	const level = DialogSystem.showDialog(frame);

	$("#ods-mapping-tabs").tabs();

	elmts.closeButton.click(function() {
		DialogSystem.dismissUntil(level - 1);
	});

	elmts.resetButton.click(function() {
		OdsSchemaAlignmentDialog.initColumnMapping(true);
		OdsSchemaAlignmentDialog.buildTableHtml();
	});

	elmts.odsPreviewTabHeading.click(function() {
		Refine.postProcess(
			"nsidr",
			"preview-digital-specimens",
			{},
			{
				columnMapping: JSON.stringify(columnMapping)
			},
			{},
			{
				onDone: function(data) {
					$("#ods-mapping-dialog-preview").text(JSON.stringify(data, null, 2));
				},
				onError: function(e) {
					console.log("preview-digital-specimens on error!");
				},
			}
		);
	});

	elmts.saveSchemaButton.click(function() {
		Refine.postProcess(
			"nsidr",
			"save-schema",
			{},
			{
				columnMapping: JSON.stringify(columnMapping),
				limit: 5
			},
			{
				modelsChanged: true
			},
			{
				onDone: function(data) {
					if (data.code === "ok") {
						alert("Schema successfully saved");
					} else {
						alert("Error - schema could not be saved. Message: " + data.message);
					}
				},
				onError: function(data) {
					alert("Error - schema could not be saved. Message: " + data.message);
				}
			})
	});

	OdsSchemaAlignmentDialog.initColumnMapping(false);
	OdsSchemaAlignmentDialog.buildTableHtml();
};

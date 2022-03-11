/*const OdsSchemaAlignmentDialog = {};
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
}*/
const OdsSchemaAlignmentDialog = {};
let columnMapping;
const defaultColumnMapping = {
	"mappingType": "digitalObject",
	"digitalObjectType": "DigitalSpecimen",
	"id": {
		"mappingType": "attribute",
		"mapping": null,
	},
	"values": {
		"ods:authoritative": {
			"mappingType": "compositeAttribute",
			"values": {
				"ods:curatedObjectID": {
					"mappingType": "attribute",
					"mapping": null,
				},
				"ods:midsLevel": {
					"mappingType": "attribute",
					"default": 1
				},
				"ods:name": {
					"mappingType": "attribute",
					"mapping": null,
				},
				"ods:institution": {
					"mappingType": "attribute",
					"mapping": null,
				},
				"ods:institutionCode": {
					"mappingType": "attribute",
					"mapping": null,
				},
				"ods:materialType": {
					"mappingType": "attribute",
					"mapping": null,
				}
			}
		},
		"ods:mediaCollection": {
			"mappingType": "digitalObject",
			"digitalObjectType": "MediaCollection",
			"id": {
				"mappingType": "attribute",
				"mapping": null,
			},
			"values": {
				"ods:mediaObjects": {
					"mappingType": "arrayAttribute",
					"values": []
				}
			}
		},
		"ods:extended": {
			"mappingType": "compositeAttribute",
			"values": {}
		}
	}
};
const presetsMappings = {
	"mediaObject": {
		"mappingType": "compositeAttribute",
		"values": {
			"mediatype": {
				"mappingType": "attribute",
				"default": "ImageObject",
				"mapping": null,
			},
			"ods:mediaId": {
				"mappingType": "attribute",
				"mapping": null,
			},
			"ods:mediaUrl": {
				"mappingType": "attribute",
				"mapping": null,
			},
			"ods:imageWidth": {
				"mappingType": "attribute",
				"mapping": null,
			},
			"ods:imageHeight": {
				"mappingType": "attribute",
				"mapping": null,
			},
			"ods:imageSizeUnit": {
				"mappingType": "attribute",
				"mapping": null,
			}
		}
	}
}


function addMappingElement(attributeName, value, pathAsArray) {
	let mapping = columnMapping;
	for (let i = 0; i < pathAsArray.length; i++) {
		mapping = mapping[pathAsArray[i]];
	}
	if (mapping.mappingType === "arrayAttribute") {
		mapping.values.push(value);
	} else {
		mapping.values[attributeName] = value;
	}
	OdsSchemaAlignmentDialog.buildTableHtml();
}

function createTableHtmlForCol(attributeName, pathAsArray, columns, attributeData) {
	const indent = pathAsArray.length * 5;

	const htmlRow = $('<tr/>');
	const name = $('<td/>', { css: { "padding-left": indent + "px" } }).text(attributeName);
	htmlRow.append(name);
	const tdSelect = $('<td/>');
	const tdDefault = $('<td/>');
	if (attributeData.default) {
			tdDefault.append($("<span/>").html(attributeData.default + "&nbsp;"),
				$("<button/>", {
					on: {
						click: function() {
							attributeData.default = null;
							OdsSchemaAlignmentDialog.buildTableHtml();
						}
					}
				}).text("x"));
	} else {
		if(attributeData.generateScopedId){
			tdDefault.text("ID will be generated when mapping field is empty string");
		}
		const defaultInput = $("<input/>");
		tdDefault.append(defaultInput,
			$("<button/>", {
				on: {
					click: function() {
						attributeData.mapping = null;
						attributeData.default = defaultInput.val();
						OdsSchemaAlignmentDialog.buildTableHtml();
					}
				}
			}).text(">"));
		const select = $('<select/>', {
			on: {
				click: function() {
					attributeData.mapping = parseInt(this.value);
				}
			}
		});
		select.append($("<option/>").text("--Select--").val(null));
		const storedIndex = attributeData.mapping;
		for (let j = 0; j < columns.length; j++) {
			const option = $("<option/>");
			const idx = columns[j].cellIndex;
			option.val(idx).text(columns[j].name);
			if (storedIndex !== undefined && storedIndex === idx) {
				option.prop('selected', true);
			}
			select.append(option);
		}
		tdSelect.append(select);
	}

	htmlRow.append(tdSelect);
	htmlRow.append($('<td/>'));
	htmlRow.append(tdDefault);

	const tdAction = $("<td/>");
	const selectAction = $("<select/>", {
		on: {
			change: function() {
				if (this.value === "delete") {
					let mapping = columnMapping;
					for (let i = 0; i < pathAsArray.length - 1; i++) {
						mapping = mapping[pathAsArray[i]];
					}
					delete mapping[attributeName];
					OdsSchemaAlignmentDialog.buildTableHtml();
				} else if (this.value === "generateScopedId") {
					attributeData.generateScopedId = true;
					attributeData.default = null;
					OdsSchemaAlignmentDialog.buildTableHtml();
				}
			}
		}
	});
	selectAction.append(
		$("<option/>").val("").text(""),
		$("<option/>").val("generateScopedId").text("Generate ID"),
		$("<option/>").val("delete").text("Delete")
	);
	tdAction.append(selectAction);
	htmlRow.append(tdAction);
	return htmlRow;
}

function serializeSchemaRecursive(tbody, schemaObjectPart, pathAsArray, key, columns) {
	let htmlRow = $('<tr/>');

	if (schemaObjectPart.mappingType === "digitalObject") {
		htmlRow.addClass("digitalobject");
	}
	const pathAsArrayParent = [...pathAsArray];
	if (key !== "Target digital object") {
		pathAsArrayParent.push(key);
	}
	const pathAsArrayChild = [...pathAsArrayParent];
	pathAsArrayChild.push("values");


	if (schemaObjectPart !== null && typeof schemaObjectPart === "object" && schemaObjectPart.mappingType !== "attribute") {
		// then it is a subsection
		const indent = pathAsArray.length * 5;
		const header = $("<th/>", { colspan: 2, css: { "padding-left": indent + "px" } }).append(key);

		const tdAction = $("<td/>");
		const selectAction = $("<select/>", {
			on: {
				change: function() {
					if (this.value === "delete") {
						let mapping = columnMapping;
						for (let i = 0; i < pathAsArrayParent.length - 1; i++) {
							mapping = mapping[pathAsArrayParent[i]];
						}
						delete mapping[key];
						OdsSchemaAlignmentDialog.buildTableHtml();
					} else if (this.value === "add") {
						let newValue = null;
						let attrName = "";
						let dataType;

						const inputHtmlType = $("<span><span>Type name: </span></span>");
						const inputType = $("<input/>");
						const okButtonType = $("<button/>", {
							class: "button",
							on: {
								click: function() {
									newValue.digitalObjectType = inputType.val();
									addMappingElement(attrName, newValue, pathAsArrayParent);
								}
							}
						}).text("Enter");
						inputHtmlType.append(inputType, okButtonType);

						const inputHtmlAttr = $("<span><span>Attribute name: </span></span>");
						const input = $("<input/>");
						const okButton = $("<button/>", {
							class: "button",
							on: {
								click: function() {
									attrName = input.val();
									switch (dataType) {
										case "attribute":
											newValue = {
												"mappingType": "attribute",
												"mapping": null
											}
											break;
										case "compositeAttribute":
											newValue = {
												"mappingType": "compositeAttribute",
												"values": {}
											};
											break;
										case "digitalObject":
											newValue = {
												"mappingType": "digitalObject",
												"values": {},
												"id": {
													"mappingType": "attribute",
													"mapping": null
												},
												"digitalObjectType": null
											};
											break;
										case "arrayAttribute":
											newValue = {
												"mappingType": "arrayAttribute",
												"values": []
											};
											break;
									}
									if (dataType === "digitalObject") {
										tdAction.append(inputHtmlType);
										inputHtmlAttr.remove();
									} else {
										addMappingElement(attrName, newValue, pathAsArrayParent);
									}
								}
							}
						}).text("Enter");
						inputHtmlAttr.append(input, okButton);


						const selectPreset = $("<select/>", {
							on: {
								change: function() {
									newValue = presetsMappings[this.value];
									if (schemaObjectPart.mappingType === "arrayAttribute") {
										okButton.click();
									} else {
										tdAction.append(inputHtmlAttr);
										this.remove();
									}
								}
							}
						});
						selectPreset.append($("<option value=\"null\">-Select Preset-</option>"));
						const presetsKeys = Object.keys(presetsMappings);
						for (let i = 0; i < presetsKeys.length; i++) {
							const preset = presetsKeys[i];
							selectPreset.append($("<option/>").val(preset).text(preset));
						}


						const select = $("<select/>", {
							on: {
								change: function() {
									switch (this.value) {
										case "attribute":
										case "compositeAttribute":
										case "arrayAttribute":
										case "digitalObject":
											dataType = this.value;
											if (schemaObjectPart.mappingType === "arrayAttribute") {
												okButton.click();
											} else {
												tdAction.append(inputHtmlAttr);
												this.remove();
											}
											break;
										case "preset":
											tdAction.append(selectPreset);
											this.remove();
											break;
										// default do nothing
									}
								}
							}
						});
						select.append(
							$("<option value=\"null\">--Select---</option>"),
							$("<option value=\"attribute\">Attribute</option>"),
							$("<option value=\"compositeAttribute\">Composite attribute</option>"),
							$("<option value=\"arrayAttribute\">Array attribute</option>"),
							$("<option value=\"digitalObject\">Digital Object</option>"),
							$("<option value=\"preset\">Preset...</option>")
						)
						tdAction.append(select);
						this.remove();
					}
				}
			}
		});
		selectAction.append(
			$("<option/>").val("").text(""),
			$("<option/>").val("add").text("Add")
		);
		if (key !== "Target digital object") {
			selectAction.append($("<option/>").val("delete").text("Delete"));
		}
		tdAction.append(selectAction);


		const tdDigitalSpecimenType = $("<td/>");
		if (schemaObjectPart.mappingType === "digitalObject") {
			tdDigitalSpecimenType.text(schemaObjectPart["digitalObjectType"]);
		}
		htmlRow.append(header).append(tdDigitalSpecimenType, $("<td/>"), tdAction, $("<td/>)"));
		tbody.append(htmlRow);
		if (schemaObjectPart.mappingType === "digitalObject") {
			const key = "id";
			const storedIndex = schemaObjectPart[key];
			tbody.append(createTableHtmlForCol(key, [...pathAsArrayParent, key], columns, storedIndex))
		}
		const objectKeys = Object.keys(schemaObjectPart.values);
		for (let i = 0; i < objectKeys.length; i++) {
			const key = objectKeys[i];
			const value = schemaObjectPart.values[key];
			serializeSchemaRecursive(tbody, value, pathAsArrayChild, key, columns);
		}
	} else {
		// then it is a schema mapping term
		tbody.append(createTableHtmlForCol(key, pathAsArrayParent, columns, schemaObjectPart));
	}
	return tbody;
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
	let tbody = $('<tbody/>');
	tbody.append(
		$("<tr/>").append(
			$("<th/>").text("Term"),
			$("<th/>").text("Mapping"),
			$("<th/>").text("Type"),
			$("<th/>").text("Default"),
			$("<th/>").text("Action")
		)
	);
	tbody = serializeSchemaRecursive(tbody, columnMapping, [], "Target digital object", columns);
	$("#ods-mapping-tabs-schema>div>table").empty().append(tbody);
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

	elmts.changeTargetDOButton.click(function() {
		elmts.changeTargetArea.show();
	});

	elmts.changeTargetDOEnter.click(function() {
		columnMapping.digitalObjectType = elmts.changeTargetDOInput.val();
		elmts.changeTargetArea.hide();
		OdsSchemaAlignmentDialog.buildTableHtml();
	});

	OdsSchemaAlignmentDialog.initColumnMapping(false);
	OdsSchemaAlignmentDialog.buildTableHtml();
};

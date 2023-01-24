const ChangesManagementComponent = {};

const ResolveDialog = {
	_comparisonSummary: {}
}
ResolveDialog.launch = function(changeToPerform) {
	const frame = $(DOM.loadHTML("dissco-uploader", "scripts/dialogs/resolve-change-dialog.html"));
	const elmts = this.elmts = DOM.bind(frame);
	const level = DialogSystem.showDialog(frame);

	elmts.cancelButton.click(function() {
		DialogSystem.dismissUntil(level - 1);
	});

	const operation = changeToPerform.split("*")[0];
	const path = changeToPerform.split("*")[1];

	const columnMapping = theProject.overlayModels.disscoUploadSchema.columnMapping;

	let endpoint = "";
	const existingMapping = ResolveDialog.jsonPointerToMappedColumn(columnMapping, path);
	const params = {
		changeToPerform: changeToPerform
	}
	let updateColumnMappingRequired = false;
	if (operation === "add" && !existingMapping) {
		let newColumnName = path;
		const columnNames = theProject.columnModel.columns.map(function(x) { return x.originalName });
		while (columnNames.includes(newColumnName)) {
			newColumnName += 1
		}
		params.newColumnName = newColumnName;
		// will append the new column at the end
		// note: this may not be the same as the cellIndex the new column gets assigned
		const columnInsertIndex = theProject.columnModel.columns.length;
		params.columnInsertIndex = columnInsertIndex;
		updateColumnMappingRequired = true;
		elmts.actionText.text("This will create a new column '" + newColumnName + "' in your dataset with the remote data from the DiSSCo infrastructure");
		endpoint = "perform-change-create";
	} else {
		const columnToBeUpdatedIndex = existingMapping.mapping;
		params.cellIndex = columnToBeUpdatedIndex;
		const mappedColumn = theProject.columnModel.columns.find(function(x) { return x.cellIndex === columnToBeUpdatedIndex });
		if(mappedColumn === undefined){
			elmts.actionText.text("Error: Found a mapping for '" + path + "' in your schema mapping but the mapped column is value is empty or does not exist. Please remove this entry or set a correct mapped column.");
			elmts.okButton.hide();
			return;
		}
		columnName = mappedColumn.originalName; 
		elmts.actionText.text("This will update the column '" + columnName + "' in your dataset with the remote data from the DiSSCo infrastructure");
		endpoint = "perform-change";
	}

	elmts.okButton.click(function() {
		Refine.postProcess(
			"dissco-uploader",
			endpoint,
			{},
			params,
			{
				everythingChanged: true
			},
			{
				onDone: function(data){
					
				},
				onFinallyDone: function() {
					DialogSystem.dismissUntil(level - 1);
					delete ChangesManagementComponent._comparisonSummary[changeToPerform];
					ChangesManagementComponent.buildTableHtml();
					if (updateColumnMappingRequired) {
						const newColumn = theProject.columnModel.columns.find(function(x) { return x.originalName === params.newColumnName });
						// update the columnMapping object in JavaScript:
						ResolveDialog.addToColumnMapping(columnMapping, path, newColumn.cellIndex);

						Refine.postProcess(
							"dissco-uploader",
							"save-schema",
							{},
							{
								columnMapping: JSON.stringify(columnMapping),
							},
							{},
							{
								onDone: function(data) {
									if (data.code === "ok") {
										alert("Column mapping schema updated and successfully saved");
										theProject.overlayModels.disscoUploadSchema.columnMapping = columnMapping;
									} else {
										alert("Error - column mapping schema could not be saved. Message: " + data.message);
									}
								},
								onError: function(data) {
									alert("Error - column mapping schema could not be saved. Message: " + data.message);
								}
							});
					}
				},
				onError: function(data) {
					alert("Error -. Message: " + data.message);
				}
			})
	});
}

ResolveDialog.jsonPointerToMappedColumn = function(columnMapping, jsonPointer) {
	const locations = jsonPointer.split("/");
	for (let i = 0; i < locations.length; i++) {
		const location = locations[i];
		if (location === "") {
			continue;
		}
		if (location in columnMapping.values) {
			columnMapping = columnMapping.values[location];
		}
	}
	if (columnMapping.mapping !== undefined) {
		// found existing column mapping for this property
		return columnMapping;
	} else {
		return undefined;
	}
}

ResolveDialog.addToColumnMapping = function(columnMapping, jsonPointer, newColumnIndex) {
	const locations = jsonPointer.split("/");
	for (let i = 0; i < locations.length; i++) {
		const location = locations[i];
		if (location === "") {
			continue;
		}
		if (i === locations.length - 1) {
			// reached end, create new column mapping entry
			columnMapping.values[location] = { mappingType: "attribute", mapping: newColumnIndex }
		} else {
			if (!Object.keys(columnMapping.values).includes(location)) {
				columnMapping.values[location] = { mappingType: "compositeAttribute", values: {} };
			}
			columnMapping = columnMapping.values[location];
			//TODO: Handle case that jsonPointer location is array index
		}
	}
}

ChangesManagementComponent.buildTableHtml = function() {
	const columns = theProject.columnModel.columns;
	let tbody = $('<tbody/>');
	tbody.append(
		$("<tr/>").append(
			$("<th/>").text("Change type"),
			$("<th/>").text("Affected rows"),
			$("<th/>").text("Path"),
			$("<th/>").text("Action"),
			$("<th/>").text("Else consequence"),
		)
	);
	const comparisonSummary = ChangesManagementComponent._comparisonSummary;
	Object.entries(comparisonSummary).forEach(function(entry) {
		const operationsMapping = {
			"add": ["Missing property locally exists at DiSSCo", "Will be removed"],
			"replace": ["Changed property at DiSSCo", "Will be overwritten"],
			"remove": ["Local property missing at DiSSCo", "Will be created"],
		}
		const ignorePaths = ["/created", "/digitalMediaObjects", "/midsLevel", "/version"];

		const key = entry[0];
		const count = entry[1];
		const operation = key.split("*")[0];
		const actionTD = $("<td/>");
		if (operation === "add" || operation === "replace") {
			actionTD.append($("<button/>", {
				on: {
					click: function() {
						ResolveDialog.launch(key, "colnametodo")
					}
				}
			}).text("Pull"),
			)
		}
		const operationText = operationsMapping[operation];		
		const path = key.split("*")[1];
		let operationConsequenceText = operationText[1];
		if(ignorePaths.includes(path)){
			operationConsequenceText = "Cannot be overwritten";
		}
		// TODO: need to handle better /digitalmediaobjects and /annotations
		tbody.append(
			$("<tr/>").append(
				$("<td/>").text(operationText[0]),
				$('<td class="text-centered"/>').text(count),
				$("<td/>").text(path),
				actionTD,
				$("<td/>").text(operationConsequenceText))
		);
	})
	$("#dissco-changes-summary").empty().append(tbody);
}

ChangesManagementComponent.init = function(elmts) {
	elmts.dialogBody.append($("<table/>").attr("id", "dissco-changes-summary"));
}

ChangesManagementComponent.start = function(elmts, comparisonSummary) {
	elmts.dialogHeader.text("Changes managament");
	elmts.dialogBody.empty();
	ChangesManagementComponent.init(elmts);
	ChangesManagementComponent._comparisonSummary = comparisonSummary;
	ChangesManagementComponent.buildTableHtml();
};

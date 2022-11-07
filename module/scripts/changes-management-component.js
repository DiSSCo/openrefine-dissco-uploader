const ChangesManagementComponent = {};

ChangesManagementComponent.buildTableHtml = function(comparisonSummary) {
	const columns = theProject.columnModel.columns;
	let tbody = $('<tbody/>');
	tbody.append(
		$("<tr/>").append(
			$("<th/>").text("Change type"),
			$("<th/>").text("Affected rows"),
			$("<th/>").text("Path"),
			$("<th/>").text("Action")
		)
	);
	Object.entries(comparisonSummary).forEach(function(entry){
		const operationsMapping = {
			"add": "New property at DiSSCo",
			"replace":"Changed property at DiSSCo"
		}
		const key = entry[0].split("-");
		const count = entry[1];
		const operation = key[0];
		const path = key[1];
		console.log("operation: " + operation, operationsMapping[operation])
		tbody.append(
		$("<tr/>").append(
			$("<td/>").text(operationsMapping[operation]),
			$("<td/>").text(count),
			$("<td/>").text(path),
			$("<td/>")
			)
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
	ChangesManagementComponent.buildTableHtml(comparisonSummary);
	};

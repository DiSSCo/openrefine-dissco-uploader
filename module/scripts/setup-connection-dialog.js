const SetupConnectionDialog = {};

function setConnectionDefaults(elmts){
}

SetupConnectionDialog.launch = function() {
	const frame = $(DOM.loadHTML("nsidr", "scripts/dialogs/setup-connection-dialog.html"));
	const elmts = this.elmts = DOM.bind(frame);

	const level = DialogSystem.showDialog(frame);

	$("#setup-connection-tabs").tabs();

	elmts.closeButton.click(function() {
		DialogSystem.dismissUntil(level - 1);
	});

	elmts.resetButton.click(function() {
		SetupConnectionDialog.setConnectionDefaults(elmts);
	});
	
	elmts.saveButton.click(function() {
		Refine.postProcess(
			"nsidr",
			"save-connection",
			{},
			{
				cordraServerUrl: elmts.inputDSServer.val(),
				authServerUrl: elmts.inputAuthServer.val(),
				authRealm: elmts.inputAuthRealm.val(),
				authClientId: elmts.inputAuthClientId.val(),
			},
			{
				modelsChanged: true
			},
			{
				onDone: function(data) {
					if (data.code === "ok") {
						alert("Connection successfully saved");
					} else {
						alert("Error - schema could not be saved. Message: " + data.message);
					}
				},
				onError: function(data) {
					alert("Error - schema could not be saved. Message: " + data.message);
				}
			})
	});
	
	SetupConnectionDialog.setConnectionDefaults(elmts);
	const schema = theProject.overlayModels.disscoSchema;
	if(schema){
		const cordraServerUrl = schema.cordraServerUrl;
		if(cordraServerUrl){
			elmts.inputDSServer.val(cordraServerUrl);
		}
		const authServerUrl = schema.authServerUrl;
		if(authServerUrl){
			elmts.inputAuthServer.val(authServerUrl);
		}
		const authRealm = schema.authRealm;
		if(authRealm){
			elmts.inputAuthRealm.val(authRealm);
		}
		const authClientId = schema.authClientId;
		if(authClientId){
			elmts.inputAuthClientId.val(authClientId);
		}
	}
};


SetupConnectionDialog.setConnectionDefaults = function(elmts){
	console.log("elmts", elmts)
	elmts.inputDSServer.val("https://nsidr.org");
	elmts.inputAuthServer.val("https://login-demo.dissco.eu/auth");
	elmts.inputAuthRealm.val("SynthesysPlus");
	elmts.inputAuthClientId.val("cordra");
}
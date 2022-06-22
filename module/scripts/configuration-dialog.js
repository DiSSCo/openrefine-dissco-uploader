const ConfigurationDialog = {};

ConfigurationDialog.launch = function() {
	const frame = $(DOM.loadHTML("cordra-uploader", "scripts/dialogs/configuration-dialog.html"));
	const elmts = this.elmts = DOM.bind(frame);

	const level = DialogSystem.showDialog(frame);

	$("#configuration-tabs").tabs();

	elmts.closeButton.click(function() {
		DialogSystem.dismissUntil(level - 1);
	});

	elmts.resetButton.click(function() {
		ConfigurationDialog.setConnectionDefaults(elmts);
	});
	
	elmts.saveButton.click(function() {
		let numberOfProcessingThreads = parseInt(elmts.numberOfProcessingThreads.val());
		if(isNaN(numberOfProcessingThreads)) {
			numberOfProcessingThreads = 1;
		}
		Refine.postProcess(
			"cordra-uploader",
			"save-connection",
			{},
			{
				cordraServerUrl: elmts.inputDOServer.val(),
				uploadingToDiSSCoInfrastructure: elmts.inputCheckboxDisscoSpecimenUpload.prop("checked"),
				authServerUrl: elmts.inputAuthServer.val(),
				authRealm: elmts.inputAuthRealm.val(),
				authClientId: elmts.inputAuthClientId.val(),
				numberOfProcessingThreads: numberOfProcessingThreads
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
	
	ConfigurationDialog.setConnectionDefaults(elmts);
	const schema = theProject.overlayModels.cordraUploadSchema;
	if(schema){
		const cordraServerUrl = schema.cordraServerUrl;
		if(cordraServerUrl){
			elmts.inputDOServer.val(cordraServerUrl);
		}
		const uploadingToDiSSCoInfrastructure = schema.uploadingToDiSSCoInfrastructure;
		if(uploadingToDiSSCoInfrastructure !== undefined){
			elmts.inputCheckboxDisscoSpecimenUpload.prop("checked", uploadingToDiSSCoInfrastructure);
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
		const numberOfProcessingThreads = schema.numberOfProcessingThreads;
		if(numberOfProcessingThreads){
			elmts.numberOfProcessingThreads.val(numberOfProcessingThreads);
		}
	}
};


ConfigurationDialog.setConnectionDefaults = function(elmts){
	elmts.inputDOServer.val("https://nsidr.org");
	elmts.inputCheckboxDisscoSpecimenUpload.prop("checked", true);
	elmts.inputAuthServer.val("https://login-demo.dissco.eu/auth");
	elmts.inputAuthRealm.val("SynthesysPlus");
	elmts.inputAuthClientId.val("cordra");
}
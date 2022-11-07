
const SynchronizationDialog = {};
let wasReconciledThisSession = false;
let syncStatesResultData = null;

let keycloak = null;

let authServerConnectionEstablished = false;
let statusOperationalText = "";

SynchronizationDialog._updateTableRowsWithSyncStates = function() {
	$(".dissco-extension-element").remove();
	$(".data-table-header > tr").prepend('<th class="column-header dissco-extension-element">Nsidr.org status</td>');

	const rows = $(".data-table > tr");
	rows.prepend(function(i) {
		let html = '<td class="dissco-extension-element" style="background-color:';
		const rowId = theProject.rowModel.rows[i].i;
		const syncState = syncStatesResultData[rowId];
		switch (syncState.syncStatus) {
			case 'synchronized':
				html += '#d4edda';
				break;
			case 'new':
				html += '#cce5ff';
				break;
			case 'error':
				html += 'red';
				break;
			case 'change':
				html += '#fff3cd';
				break;
			default:
				html += 'gray';
		}
		html += '">' + syncState.syncStatus + "</td>";
		return html;
	});
}

SynchronizationDialog.launch = function() {
	const disscoUploadSchema = theProject.overlayModels.disscoUploadSchema;
	if(disscoUploadSchema === undefined ){
		alert("You must check and save the configuration of the dissco uploader plugin once before you can begin.");
		return;
	}
	const initOptions = {
		url: (disscoUploadSchema && disscoUploadSchema.authServerUrl) ?? "",
		realm: (disscoUploadSchema && disscoUploadSchema.authRealm) ?? "",
		clientId: (disscoUploadSchema && disscoUploadSchema.authClientId) ?? "",
	};

	if (!keycloak || !keycloak.authenticated) {
		keycloak = Keycloak(initOptions);
	}

	if (!keycloak.token) {
		keycloak
			.init({
				checkLoginIframe: false,
				messageReceiveTimeout: 2500,
			})
			.then((auth) => {
				authServerConnectionEstablished = true;
				SynchronizationDialog.initWithAuthInfo(auth)
			})
			.catch(e => {
				statusOperationalText = `Failed to contact authentication server. Make sure that the configured url ${disscoUploadSchema.authServerUrl} and parameters are correct or contact the server administrator`;
				SynchronizationDialog.initWithAuthInfo(keycloak.authenticated);
			})
	} else {
		SynchronizationDialog.initWithAuthInfo(keycloak.authenticated);
	}
}

SynchronizationDialog.initWithAuthInfo = function(isAuthenticated) {
	if (isAuthenticated) {
		console.log(keycloak.tokenParsed);
		console.log(keycloak.token);
		setInterval(() => {
			keycloak
				.updateToken(30) // minimum validity of token should be 30 seconds otherwise refresh
				.then((refreshed) => {
					if (refreshed) {
						console.log(
							'Token refreshed' + refreshed,
							'will update client with new token:',
							keycloak.token
						);
					} else {
						console.log("token valid for " + Math.round(keycloak.tokenParsed.exp + keycloak.timeSkew - new Date().getTime() / 1000) + ' seconds')
					}
				})
				.catch(() => {
					console.log('Failed to refresh token');
				});
		}, 20000); // intervall every 20 seconds
	}

	const frame = $(DOM.loadHTML("dissco-uploader", "scripts/dialogs/synchronization-dialog.html"));
	const elmts = this.elmts = DOM.bind(frame);

	if (keycloak.authenticated) {
		elmts.loggedInText.text("You are currently logged in as " + keycloak.tokenParsed.preferred_username);
		elmts.loginLink.attr("href", keycloak.createLogoutUrl());
		elmts.loginLink.text("Logout");
		statusOperationalText = "You must run the Pre-Sync process before synchronization. This will compare your local data with the data at nsidr.org and list the differences for you to approve before synchronization";
		elmts.preSyncButton.attr("disabled", false);
		elmts.preSyncButton.removeClass("button-disabled");
	} else {
		if (authServerConnectionEstablished) {
			statusOperationalText = "You must login in order to start the synchronization process";
		}
		elmts.loginLink.attr("href", keycloak.createLoginUrl());
		elmts.loginLink.text("Login");
	}
	elmts.synchronizationInfoText.text(statusOperationalText);

	const level = DialogSystem.showDialog(frame);

	const schema = theProject.overlayModels.disscoUploadSchema
	if (schema && schema.columnMapping) {
		Refine.postProcess(
			"dissco-uploader",
			"preview-digital-specimens",
			{},
			{
				columnMapping: JSON.stringify(schema.columnMapping),
				limit: 1
			},
			{},
			{
				onDone: function(data) {
					$("#ods-mapping-dialog-preview").text(JSON.stringify(data, null, 2));
				},
				onError: function(e) {
					console.log("preview-digital-specimens on error!", e);
				},
			}
		);
	} else {
		$("#ods-mapping-dialog-preview").text("Error: No saved schema found");
	}

	elmts.closeButton.click(function() {
		DialogSystem.dismissUntil(level - 1);
	});

	elmts.preSyncButton.click(function() {
		Refine.postProcess(
			"dissco-uploader",
			"prepare-for-synchronization",
			{},
			{
				token: keycloak.token
			},
			{
				rowsChanged: true
			},
			{
				onDone: function(data) {
					if (data.code === "ok") {
						wasReconciledThisSession = true;
						syncStatesResultData = data.results;

						let inSyncCount = 0;
						let newCount = 0;
						let updateCount = 0;
						//To-Do: handle error for object
						const iKeys = Object.keys(syncStatesResultData);
						iKeys.forEach(function(iKey) {
							if (syncStatesResultData[iKey].syncStatus === "synchronized") {
								inSyncCount += 1;
							} else if (syncStatesResultData[iKey].syncStatus === "new") {
								newCount += 1;
							} else if (syncStatesResultData[iKey].syncStatus === "change") {
								updateCount += 1;
							}
						});

						elmts.synchronizationInfoText.text(`Ready for syncing: ${inSyncCount} objects are in sync, will upload ${newCount} new objects to nsidr.org, found ${updateCount} objects with changes - please revise these changes carefully, if you synchronize the remote objects will be overwritten with your local data`);
						elmts.syncButton.attr("disabled", false);
						elmts.syncButton.removeClass("button-disabled");
						
						ChangesManagementComponent.start(elmts, data.comparisonSummary);
					}
				},
				onFinallyDone: function() {
					// this callback does not receive the data
					SynchronizationDialog._updateTableRowsWithSyncStates();
				},
				onError: function(e) {
					console.log("preview-digital-specimens on error!", e);
				},
			}
		);
	});

	elmts.syncButton.click(function() {
		Refine.postProcess(
			"dissco-uploader",
			"perform-edits",
			{},
			{
				token: keycloak.token
			},
			{},
			{
				onFinallyDone: function() {
					// this callback does not receive the data

					Refine.postProcess(
						"dissco-uploader",
						"fetch-synchronization-status",
						{
							start: theProject.rowModel.start,
							limit: theProject.rowModel.limit
						},
						{},
						{
							rowsChanged: true
						},
						{

							onDone: function(data) {
								if (data.code === "ok") {
									syncStatesResultData = data.results;
									// handle display data.stats
									let inSyncCount = 0;
									let errorCount = 0;
									if (data.stats.synchronized) {
										inSyncCount = data.stats.synchronized;
									}
									if (data.stats.error) {
										errorCount = data.stats.error;
									}
									elmts.synchronizationInfoText.text(`Synchronization result: ${inSyncCount} objects are synchronized, ${errorCount} objects had an error during synchronization`);
								}
							},
							onFinallyDone: function() {
								SynchronizationDialog._updateTableRowsWithSyncStates();
							}
						}
					);
				},
				onError: function(e) {
					console.log("perform-edits on error", e);
				},
			}
		);
	});
}


SynchronizationDialog.showError = function(e) {
	const frame = $(DOM.loadHTML("dissco-uploader", "scripts/dialogs/synchronization-dialog.html"));
	const elmts = this.elmts = DOM.bind(frame);
	const level = DialogSystem.showDialog(frame);
	elmts.synchronizationInfoText.text("Error: Could not connect to the authentication server. Please check and save the auth server url and try again");

	elmts.closeButton.click(function() {
		DialogSystem.dismissUntil(level - 1);
	});
}

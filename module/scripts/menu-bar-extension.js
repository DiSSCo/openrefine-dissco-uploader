//extend the column header menu
$(function() {

	ExtensionBar.MenuItems.push(
		{
			"id": "cordra-uploader",
			"label": "Cordra upload",
			"submenu": [
				{
					id: "cordra-uploader/configuration",
					label: "Configuration",
					click: function() { ConfigurationDialog.launch(); }
				},
				{
					id: "cordra-uploader/edit-schema-mapping",
					label: "Edit schema mapping",
					click: function() { OdsSchemaAlignmentDialog.launch(); }
				},
				{
					id: "cordra-uploader/sync-nsidr-data",
					label: "Synchronize data with the Cordra server",
					click: function() { SynchronizationDialog.launch(); }
				}
			]
		}
	);
});

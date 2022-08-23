//extend the column header menu
$(function() {

	ExtensionBar.MenuItems.push(
		{
			"id": "dissco-uploader",
			"label": "Digital specimen upload",
			"submenu": [
				{
					id: "dissco-uploader/configuration",
					label: "Configuration",
					click: function() { ConfigurationDialog.launch(); }
				},
				{
					id: "dissco-uploader/edit-schema-mapping",
					label: "Edit schema mapping",
					click: function() { OdsSchemaAlignmentDialog.launch(); }
				},
				{
					id: "dissco-uploader/sync-nsidr-data",
					label: "Synchronize data with the digital specimen server",
					click: function() { SynchronizationDialog.launch(); }
				}
			]
		}
	);
});

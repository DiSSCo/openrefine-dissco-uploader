//extend the column header menu
$(function() {

	ExtensionBar.MenuItems.push(
		{
			"id": "dissco-nsidr",
			"label": 'DiSSCo nsidr',
			"submenu": [
				{
					id: "dissco/setup-connection",
					label: 'Setup server connection',
					click: function() { SetupConnectionDialog.launch(); }
				},
				{
					id: "dissco/edit-schema-mapping",
					label: 'Edit ODS schema mapping',
					click: function() { OdsSchemaAlignmentDialog.launch(); }
				},
				{
					id: "dissco/sync-nsidr-data",
					label: 'Synchronize data with the server',
					click: function() { SynchronizationDialog.launch(); }
				}
			]
		}
	);
});

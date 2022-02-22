//extend the column header menu
$(function () {

  ExtensionBar.MenuItems.push(
      {
        "id": "dissco-nsidr",
        "label": 'DiSSCo nsidr',
        "submenu": [
          {
            id: "dissco/edit-schema-mapping",
            label: 'Edit ODS mapping for nsidr.org',
            click: function () {OdsSchemaAlignmentDialog.launch();}
          },
          {
            id: "dissco/sync-nsidr-data",
            label: 'Synchronize data with nsidr.org',
            click: function () {NsidrSynchronizationDialog.launch();}
          }
        ]
      }
  );
});

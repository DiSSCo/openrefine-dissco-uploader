# openrefine-cordra-uploader

## Installation

### For end-users
To-Do

### Development install
(For Ubuntu or similar OS.) You need a source installation of OpenRefine in order to build this extension. Run
```bash
git clone https://github.com/OpenRefine/OpenRefine.git
# Clone this repo in the extensions folder of OpenRefine:
cd OpenRefine/extensions/
git clone https://github.com/DiSSCo/openrefine-cordra-uploader.git
cd openrefine-cordra-uploader/
# Now compile with maven
mvn compile
```
Afterwards you can run the OpenRefine source installation by `./refine` from the root directory of OpenRefine, or copy the folder with this repo's content to your local OpenRefine [workspace directory](https://docs.openrefine.org/manual/installing#set-where-data-is-stored), so you can use it with a downloaded version of OpenRefine.

## How to use this extension
After installation, a menu item "Cordra uploader" should appear in your OpenRefine GUI when editing a project.
1. Set the url of the Cordra server where you want to upload objects and the auth server url (currently only token-based authentication via an external provider is implemented, preferrably use a keycloak server).
2. Edit the schema mapping: here you create the json-like structure of the DigitalObject to create and map every attribute to a column of your data (alternatively set default values). Every row must correspond to one Digital Object. However, nested DOs are also supported, if some attributes should be stored in another DO, which is then referenced by the target DO corresponding to one row.
3. Use the synchronization dialog to upload your data.

### Notes
- In the schema mapping define an initially emtpy column for every Digital Object to be created per row, where the ID of the created DO can be stored
- Afterwards you can update existing data provided that the column which contains the object's ID is existent and correctly mapped.
- You can also upload Payloads when creating DOs. In the schema mapping dialog set at least the payload name and the path on your local Computer to the file to upload. When updating existing DOs the payloads are currently ignored.

# To-Do:
- improve visualization of sync results: show errors and diffs to user (for example on mouse over -> popup)
- for every DigitalObject in the mapping the ID field must be mandatory, if not reject saving of the column mapping
- deal with delete



# Issues:
- for synchronization process that takes longer thant the expiration time of keycloak token (e.g. massive uploads) an implementation for automatically refreshing automatically has to be implemented (probably add Java keycloak library on server-side, pass the refresh_token as POST parameter, refresh the token in Java during RowVisitor when it is close to expiry

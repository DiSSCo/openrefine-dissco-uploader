# openrefine-cordra-uploader
# To-Do:
- add instructions how to build and run
- nsidr.org url is currently hardcoded, Cordra endpoint and auth endpoint shoud be configurable via a ui dialog and stored in the Overlay schema
- improve schema mapping: allow adding of new fields to extended, differentiate between obligatory and optional fields, ui layot
- deal with delete
- test with real image use case, massive test data
- remove System.out.println lines
- refactor: inner protected class duplication, etc.
- make as long running process (see keycloak token issue below)


# Issues:
- for synchronization process that takes longer thant the expiration time of keycloak token (e.g. massive uploads) an implementation for automatically refreshing automatically has to be implemented (probably add Java keycloak library on server-side, pass the refresh_token as POST parameter, refresh the token in Java during RowVisitor when it is close to expiry

/*

Copyright 2010, Google Inc.
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

* Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above
copyright notice, this list of conditions and the following disclaimer
in the documentation and/or other materials provided with the
distribution.
* Neither the name of Google Inc. nor the names of its
contributors may be used to endorse or promote products derived from
this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

*/

var html = "text/html";
var encoding = "UTF-8";
var ClientSideResourceManager = Packages.com.google.refine.ClientSideResourceManager;

/*
* Function invoked to initialize the extension.
*/
function init() {
	// Packages.java.lang.System.err.println(module.getMountPoint());
	var RefineServlet = Packages.com.google.refine.RefineServlet;
	RefineServlet.registerCommand(module, "save-connection", new Packages.eu.dissco.refineextension.commands.SaveConfigurationCommand());
	RefineServlet.registerCommand(module, "save-schema", new Packages.eu.dissco.refineextension.commands.SaveSchemaCommand());
	RefineServlet.registerCommand(module, "preview-digital-specimens", new Packages.eu.dissco.refineextension.commands.PreviewDigitalSpecimenCommand());
	RefineServlet.registerCommand(module, "perform-edits", new Packages.eu.dissco.refineextension.commands.PerformEditsCommand());
	RefineServlet.registerCommand(module, "prepare-for-synchronization", new Packages.eu.dissco.refineextension.commands.PrepareForSynchronizationCommand());
	RefineServlet.registerCommand(module, "fetch-synchronization-status", new Packages.eu.dissco.refineextension.commands.GetSynchronizationResultCommand());
	RefineServlet.registerCommand(module, "perform-change", new Packages.eu.dissco.refineextension.commands.PerformChangeCommand());
	RefineServlet.registerCommand(module, "perform-change-create", new Packages.eu.dissco.refineextension.commands.PerformChangeCreateCommand());
	// Script files to inject into /project page
	ClientSideResourceManager.addPaths(
		"project/scripts",
		module,
		[
			"scripts/lib/keycloak.js",
			"scripts/project-injection.js",
			"scripts/menu-bar-extension.js",
			"scripts/configuration-dialog.js",
			"scripts/schema-alignment-dialog.js",
			"scripts/changes-management-component.js",
			"scripts/synchronization-dialog.js",
		]
	);

	// Style files to inject into /project page
	ClientSideResourceManager.addPaths(
		"project/styles",
		module,
		[
			"styles/project-injection.less"
		]
	);

	Packages.com.google.refine.model.Project.registerOverlayModel("disscoUploadSchema", Packages.eu.dissco.refineextension.schema.DisscoUploadSchema);
}

/*
* Function invoked to handle each request in a custom way.
*/
function process(path, request, response) {
	// Analyze path and handle this request yourself.

	if (path == "/" || path == "") {
		var context = {};
		// here's how to pass things into the .vt templates

		send(request, response, "index.vt", context);
	}
}

function send(request, response, template, context) {
	butterfly.sendTextFromTemplate(request, response, context, template, encoding, html);
}

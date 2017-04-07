#!/usr/bin/env node

// Create vaadin-addons/autogenerated.html containing import statements for all html files
// found inside imports in vaadin-addons/bower.json
const fs = require('fs');

var addonsBower = JSON.parse(fs.readFileSync("vaadin-addons/bower.json"));
var addonsDeps = addonsBower.dependencies;

var dependencies = Object.keys(addonsDeps);
var toImport = [];

dependencies.forEach(dependency => {
    var folder = "bower_components/" + dependency;
    var files = fs.readdirSync(folder);
    files.forEach(file => {
        if (file.indexOf("-") != -1 && file.endsWith("html")) {
          // Assume that custom elements are defined inside their own file,
          // which thus must contain a "-". This excludes typical demo/test
          // files such as index.html
          toImport.push("<link rel=\"import\" href=\"" + folder + "/" + file + "\">");
        }
    });
});

var oldBundle = "";
var bundleFile = "bundle.html";
if (fs.existsSync(bundleFile)) {
	oldBundle = fs.readFileSync(bundleFile);
}
var newBundle = toImport.join("\n")+"\n";

if (oldBundle == newBundle) {
	// No changes
	// process.exit(1);
} else {
	fs.writeFileSync(bundleFile, newBundle);
}

var configFileEditor;
function initConfigFileEditor() {
	var configFileTextArea = document.getElementById("configFileEditor");
	let type = getTypeOfConfigFile();
	alert("Type: " + type);
	console.error("Type: " + type);
	if (configFileTextArea) {
		alert("Type: " + type);
		console.error("Type: " + type);
		configFileEditor = CodeMirror.fromTextArea(configFileTextArea, {
			lineNumbers: true,
			mode: type
		});
		setTimeout(function() {
			configFileEditor.refresh();
		}, 100);
		configFileEditor.on('change', editor => {
			document.getElementById("configFileEditor").innerHTML = editor.getValue();
		});
	}
	window.addEventListener('resize', function() {
		setHeightOfTextEditor();
	});
	setHeightOfTextEditor();
}
function loadEditorContent() {
	var configFileTextArea = document.getElementById("configFileEditor");
	configFileTextArea.innerHTML = configFileEditor.getValue();
}
function loadEditorContentAndInit() {
	loadEditorContent();
	initConfigFileEditor();
}
function setHeightOfTextEditor() {
	var documentHeight = document.body.clientHeight;
	var offset = $("#boxUntilBottom").offset();
	var xPosition = offset.left - $(window).scrollLeft();
	var yPosition = offset.top - $(window).scrollTop();
	var border = 20;
	var resultHeight = documentHeight - yPosition - border;
	var box = document.getElementById("boxUntilBottom");
	box.style.height = resultHeight + "px";
	var codeMirror = document.getElementsByClassName("CodeMirror")[0];
	codeMirror.style.height = "100%";
	var borderBox = document.getElementById("configFileEditorBorder");
	// 100 is an estimated height of buttons and spaces
	borderBox.style.height = (resultHeight - 100) + "px";
}
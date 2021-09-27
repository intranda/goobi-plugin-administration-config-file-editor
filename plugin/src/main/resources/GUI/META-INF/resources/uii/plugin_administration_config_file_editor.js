var configFileEditor;
function initConfigFileEditor() {
	var configFileTextArea = document.getElementById("configFileEditor");
	let type = "xml";
	let typeElement = document.getElementById("currentConfigFileType");
	if (typeElement) {
		type = typeElement.innerHTML.trim();
	}
	if (configFileTextArea) {
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
	var configFileTextAreaBase64 = document.getElementById("configFileEditorForm:configFileEditorBase64");
	let string = configFileEditor.getValue();
	console.log("Load: " + string);
	let index = 0;
	while (index < string.length) {
		let character = string.charAt(index);
		if (character > 127) {
			alert(character);
		}
		index++;
	}
	configFileTextAreaBase64.value = base64EncodeUnicode(configFileEditor.getValue());
}

function base64EncodeUnicode(str) {
  // Firstly, escape the string using encodeURIComponent to get the UTF-8 encoding of the characters, 
  // Secondly, we convert the percent encodings into raw bytes, and add it to btoa() function.
  utf8Bytes = encodeURIComponent(str).replace(/%([0-9A-F]{2})/g, function (match, p1) {
    return String.fromCharCode('0x' + p1);
  });

  return btoa(utf8Bytes);
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
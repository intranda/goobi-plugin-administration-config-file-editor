package de.intranda.goobi.plugins.xml;

import java.util.ArrayList;
import java.util.List;

import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import lombok.Getter;

public class ReportErrorsErrorHandler implements ErrorHandler {

    @Getter
    private List<XMLError> errors;

    public ReportErrorsErrorHandler() {
        errors = new ArrayList<XMLError>();
    }

    private void addError(SAXParseException e, String severity) {
        errors.add(new XMLError(e.getLineNumber(), e.getColumnNumber(), severity, e.getMessage()));

    }

    @Override
    public void error(SAXParseException exception) throws SAXException {
        addError(exception, "ERROR");
    }

    @Override
    public void fatalError(SAXParseException exception) throws SAXException {
        addError(exception, "FATAL");
    }

    @Override
    public void warning(SAXParseException exception) throws SAXException {
        addError(exception, "WARNING");
    }

}

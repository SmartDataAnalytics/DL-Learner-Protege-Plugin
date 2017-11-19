package org.dllearner.tools.protege;

import org.dllearner.core.EvaluatedAxiom;
import org.semanticweb.owlapi.model.OWLAxiom;

import javax.swing.*;
import java.util.List;

public class OWLAxiomHypothesesTableModel extends AbstractHypothesesTableModel<EvaluatedAxiom> {

	private static final long serialVersionUID = -6920806148989403795L;

	public OWLAxiomHypothesesTableModel(List<EvaluatedAxiom> evaluatedAxioms){
		super(evaluatedAxioms);
	}

	@Override
	public Object getValueAt(int rowIndex, int columnIndex) {
		EvaluatedAxiom suggestion = hypotheses.get(rowIndex);
		switch (columnIndex) {
		case 0:
			return (int) (suggestion.getAccuracy() * 100);
		case 1:
			break;
		case 2:
			return suggestion.getAxiom();
		}
		return null;

	}
	
	@Override
	public Class<?> getColumnClass(int columnIndex) {
		switch (columnIndex) {
		case 0:
			return String.class;
		case 1:
			return Icon.class;
		case 2:
			return OWLAxiom.class;
		}
		return null;
	}
	@Override
	public String getColumnName(int column){
		if(column == 0){
			return "Accuracy";
		} else if (column == 2){
			return "Axiom";
		} else {
			return "";
		}
	}
}

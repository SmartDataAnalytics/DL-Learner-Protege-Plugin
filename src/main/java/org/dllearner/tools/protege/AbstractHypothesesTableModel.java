package org.dllearner.tools.protege;

import org.dllearner.core.EvaluatedHypothesis;
import org.dllearner.learningproblems.EvaluatedDescriptionClass;
import org.semanticweb.owlapi.model.OWLClassExpression;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AbstractHypothesesTableModel<T extends EvaluatedHypothesis> extends AbstractTableModel {

	private static final long serialVersionUID = -6920806148989403795L;

	protected List<T> hypotheses = new ArrayList<>();

	private final Icon inconsistentIcon = new ImageIcon(this.getClass().getResource("warning-icon.png"));
	private final Icon followsIcon = new ModelsIcon();

	public AbstractHypothesesTableModel(List<T> hypotheses){
		this.hypotheses = hypotheses;
	}

	@Override
	public int getColumnCount() {
		return 3;
	}

	@Override
	public int getRowCount() {
		return hypotheses.size();
	}

	@Override
	public Object getValueAt(int rowIndex, int columnIndex) {
		T suggestion = hypotheses.get(rowIndex);
		switch (columnIndex) {
		case 0:
			return (int) (suggestion.getAccuracy() * 100);
		case 1:
			if(DLLearnerPreferences.getInstance().isCheckConsistencyWhileLearning()){
				if (suggestion instanceof EvaluatedDescriptionClass && 
						!((EvaluatedDescriptionClass)suggestion).isConsistent()) {
					return inconsistentIcon;
				} 
			}
			if(suggestion instanceof EvaluatedDescriptionClass && ((EvaluatedDescriptionClass)suggestion).followsFromKB()){
				return followsIcon;
			}
			break;
		case 2:
			return suggestion.getDescription();
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
			return OWLClassExpression.class;
		}
		return null;
	}
	@Override
	public String getColumnName(int column){
		if(column == 0){
			return "Accuracy";
		} else if (column == 2){
			return "Class expression";
		} else {
			return "";
		}
	}
	
	public Optional<T> getEntryAtRow(int row){
		if(hypotheses.size() >= row){
			return Optional.of(hypotheses.get(row));
		} else {
			return Optional.empty();
		}
	}
	
	public void clear(){
		hypotheses.clear();
		fireTableDataChanged();
	}
	
	public void setSuggestions(List<T> suggestionList){
		this.hypotheses.clear();
		this.hypotheses.addAll(suggestionList);
		fireTableDataChanged();
	}
	
	public T getSelectedValue(int rowIndex){
		return hypotheses.get(rowIndex);
	}
	
	public int getSelectionIndex(T e){
		return hypotheses.indexOf(e);
	}

}

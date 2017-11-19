package org.dllearner.tools.protege;

import java.util.ArrayList;
import java.util.List;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.table.AbstractTableModel;

import org.dllearner.core.EvaluatedDescription;
import org.dllearner.learningproblems.EvaluatedDescriptionClass;
import org.semanticweb.owlapi.model.OWLClassExpression;

public class SuggestionsTableModel extends AbstractTableModel {
	
	private static final long serialVersionUID = -6920806148989403795L;
	
	private List<EvaluatedDescription> suggestionList;
	private final Icon inconsistentIcon = new ImageIcon(this.getClass().getResource("warning-icon.png"));
	private final Icon followsIcon = new ModelsIcon();
	
	public SuggestionsTableModel(){
		super();
		suggestionList = new ArrayList<>();
	}
	
	public SuggestionsTableModel(List<EvaluatedDescription> suggestionList){
		this.suggestionList = suggestionList;
	}

	@Override
	public int getColumnCount() {
		return 3;
	}

	@Override
	public int getRowCount() {
		return suggestionList.size();
	}

	@Override
	public Object getValueAt(int rowIndex, int columnIndex) {
		EvaluatedDescription suggestion = suggestionList.get(rowIndex);
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
	
	public EvaluatedDescription getEntryAtRow(int row){
		if(suggestionList.size() >= row){
			return suggestionList.get(row);
		} else {
			return null;
		}
	}
	
	public void clear(){
		suggestionList.clear();
		fireTableDataChanged();
	}
	
	public void setSuggestions(List<? extends EvaluatedDescription> suggestionList){
		this.suggestionList.clear();
		this.suggestionList.addAll(suggestionList);
		fireTableDataChanged();
	}
	
	public EvaluatedDescription getSelectedValue(int rowIndex){
		return suggestionList.get(rowIndex);
	}
	
	public int getSelectionIndex(EvaluatedDescription e){
		return suggestionList.indexOf(e);
	}

}

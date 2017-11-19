/**
 * Copyright (C) 2007-2009, Jens Lehmann
 *
 * This file is part of DL-Learner.
 * 
 * DL-Learner is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * DL-Learner is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package org.dllearner.tools.protege;

import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.dllearner.core.EvaluatedDescription;
import org.semanticweb.owlapi.model.AxiomType;
/**
 * This is the MouseListener for the Suggest Panel.
 * @author Christian Koetteritzsch
 *
 */
public class SuggestClassPanelHandler implements  ListSelectionListener{
	private DLLearnerView view;
	private EvaluatedDescription evaluatedDescription;
	
	public SuggestClassPanelHandler(DLLearnerView v) {
		this.view = v;
	}

	@Override
	public void valueChanged(ListSelectionEvent e) {
		if(view.getSuggestClassPanel().getSuggestionsTable().getSelectedRow() >= 0){

			// get the selected class expression
			EvaluatedDescription newDesc = view.getSuggestClassPanel().getSuggestionsTable().getSelectedSuggestion();

			// enable 'Add' button
			if(!e.getValueIsAdjusting() && newDesc != null) {
				view.getAddButton().setEnabled(true);
			}

			// update the class coverage diagram
			if(view.getAxiomType().equals(AxiomType.EQUIVALENT_CLASSES) || 
					view.getAxiomType().equals(AxiomType.SUBCLASS_OF)) {
//				evaluatedDescription = view.getSuggestClassPanel().getSuggestionsTable().getSelectedSuggestion();
				if(!e.getValueIsAdjusting() && (evaluatedDescription == null || !evaluatedDescription.equals(newDesc))){
//					view.getMoreDetailForSuggestedConceptsPanel().renderDetailPanel(evaluatedDescription);
					view.showHintMessagePanel(false);
					view.showGraphicalPanel(true);
					view.getGraphicalPanel().setDescription(evaluatedDescription);
				}
			}

			evaluatedDescription = newDesc;
		}
		
		
	}

}

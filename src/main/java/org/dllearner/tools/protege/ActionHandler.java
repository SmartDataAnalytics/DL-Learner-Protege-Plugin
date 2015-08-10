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

import static org.semanticweb.owlapi.model.AxiomType.DATA_PROPERTY_DOMAIN;
import static org.semanticweb.owlapi.model.AxiomType.OBJECT_PROPERTY_DOMAIN;
import static org.semanticweb.owlapi.model.AxiomType.OBJECT_PROPERTY_RANGE;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import org.dllearner.core.EvaluatedAxiom;
import org.dllearner.core.EvaluatedDescription;
import org.dllearner.learningproblems.EvaluatedDescriptionClass;
import org.protege.editor.core.ProtegeApplication;
import org.protege.editor.core.ui.error.ErrorLogPanel;
import org.protege.editor.core.ui.progress.BackgroundTask;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLDataPropertyDomainAxiom;
import org.semanticweb.owlapi.model.OWLObjectPropertyDomainAxiom;
import org.semanticweb.owlapi.model.OWLObjectPropertyRangeAxiom;

/**
 * This class processes input from the user.
 * 
 * @author Christian Koetteritzsch
 * 
 */
public class ActionHandler implements ActionListener {
	
	public enum Actions {
		LEARN, STOP
	}


	private boolean toggled;
	private Timer timer;
	private SuggestionRetriever retriever;
	private HelpTextPanel helpPanel;
//	private final Color colorRed = new Color(139, 0, 0);
//	private final Color colorGreen = new Color(0, 139, 0);
	private final DLLearnerView view;
	
	private static final String EQUIVALENT_CLASS_LEARNING_STRING = "<html>suggest equivalent class expressions</html>";
	private static final String SUPER_CLASS_LEARNING_STRING = "<html>suggest super class expressions</html>";
	private static JOptionPane optionPane;
	
	private BackgroundTask learningTask;

	/**
	 * This is the constructor for the action handler.
	 * 
	 * @param m
	 *            DLLearnerModel
	 * @param view
	 *            DLlearner tab
	 * 
	 */
	public ActionHandler(DLLearnerView view) {
		this.view = view;
		toggled = false;
		helpPanel = new HelpTextPanel(view);
		optionPane = new JOptionPane();
	}

	/**
	 * When a Button is pressed this method select the right.
	 * 
	 * @param z
	 *            ActionEvent
	 */
	public void actionPerformed(ActionEvent z) {
		if (z.getActionCommand().equals(Actions.LEARN.toString())) {
			onLearn();
		} else if (z.getActionCommand().equals(DLLearnerView.ADD_BUTTON_STRING)) {
			onAddAxiom();
		} else if (z.toString().contains(DLLearnerView.ADVANCED_BUTTON_STRING)) {
			onShowAdvancedOptions();
		} else if (z.toString().contains(DLLearnerView.HELP_BUTTON_STRING)) {
			String currentClass = Manager.getInstance().getCurrentlySelectedClassRendered();
			optionPane.setPreferredSize(new Dimension(300, 200));
			JOptionPane.showMessageDialog(view.getLearnerView(), helpPanel.renderHelpTextMessage(currentClass), "Help",
					JOptionPane.INFORMATION_MESSAGE);
		}
	}
	
	private void onLearn() {
		setLearningOptions();
		view.showGraphicalPanel(false);
		view.getSuggestClassPanel().getSuggestionsTable().clear();
//		view.setBusyTaskStarted("Preparing ...");
//		manager.initLearningProblem();
//		manager.initLearningAlgorithm();
//		view.setBusyTaskEnded();
		
		learningTask = ProtegeApplication.getBackgroundTaskManager().startTask("Learning...");
		
//		view.setLearningStarted();
//		view.showHorizontalExpansionMessage(Manager.getInstance().getMinimumHorizontalExpansion(),
//				Manager.getInstance().getMaximumHorizontalExpansion());
//		try {
//			Manager manager = Manager.getInstance();
//			manager.initLearningProblem();
//			manager.initLearningAlgorithm();
//			Manager.getInstance().startLearning();
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
		
		retriever = new SuggestionRetriever();
		retriever.addPropertyChangeListener(view.getStatusBar());
		retriever.execute();
	}
	
	private void onAddAxiom() {
		EvaluatedDescription selectedSuggestion = view.getSuggestClassPanel().getSelectedSuggestion();
		Manager.getInstance().addAxiom(selectedSuggestion);
		String message = "<html><font size=\"3\">class expression added</font></html>";
		view.setHintMessage(message);
		view.setHelpButtonVisible(false);
	}
	
	private void onShowAdvancedOptions() {
		if (!toggled) {
			toggled = true;
			view.setIconToggled(toggled);
			view.showOptionsPanel(toggled);
		} else {
			toggled = false;
			view.setIconToggled(toggled);
			view.showOptionsPanel(toggled);
		}
	}

	/**
	 * Resets the toggled Button after the plugin is closed.
	 */
	public void resetToggled() {
		toggled = false;
	}
	
	private void setLearningOptions(){
		OptionPanel options = view.getOptionsPanel();
		Manager manager = Manager.getInstance();
		manager.setMaxExecutionTimeInSeconds(options.getMaxExecutionTimeInSeconds());
		manager.setMaxNrOfResults(options.getMaxNumberOfResults());
		manager.setNoisePercentage(options.getNoise());
		manager.setUseAllConstructor(options.isUseAllQuantor());
		manager.setUseNegation(options.isUseNegation());
		manager.setUseCardinalityRestrictions(options.isUseCardinalityRestrictions());
		manager.setUseExistsConstructor(options.isUseExistsQuantor());
		manager.setUseHasValueConstructor(options.isUseHasValue());
	}

	/**
	 * Inner Class that retrieves the concepts learned by DL-Learner.
	 * 
	 * @author Christian Koetteritzsch
	 * 
	 */
	class SuggestionRetriever
			extends
			SwingWorker<List<? extends EvaluatedDescription>, List<? extends EvaluatedDescription>> {

		@SuppressWarnings("unchecked")
		@Override
		protected List<EvaluatedDescription> doInBackground()
				throws Exception {
			SwingUtilities.invokeLater(new Runnable() {
				@Override
				public void run() {
					view.setBusyTaskStarted("Preparing ...");
				}
			});
			
			try {
				Manager manager = Manager.getInstance();
				timer = new Timer();
				if(view.getAxiomType().equals(AxiomType.EQUIVALENT_CLASSES) || view.getAxiomType().equals(AxiomType.SUBCLASS_OF)) {
					manager.initLearningProblem();
					manager.initLearningAlgorithm();
					
					view.setBusyTaskEnded();
					SwingUtilities.invokeLater(new Runnable() {
						@Override
						public void run() {
							view.setLearningStarted();
							view.showHorizontalExpansionMessage(Manager.getInstance().getMinimumHorizontalExpansion(),
									Manager.getInstance().getMaximumHorizontalExpansion());
						}
					});
					
					
//					timer.schedule(new TimerTask(){
//						int progress = 0;
//						List<EvaluatedDescriptionClass> result;
//						@Override
//						public void run() {
//							progress++;
//							setProgress(progress);
//							if(!isCancelled() && Manager.getInstance().isLearning()){
//								result = Manager.getInstance().getCurrentlyLearnedDescriptions();
//								publish(result);
//							}
//						}
//						
//					}, 1000, 500);
					Manager.getInstance().startLearning();
				} else {
					List<EvaluatedAxiom<OWLAxiom>> axioms = manager.computeAxioms(view.getEntity(), view.getAxiomType());
					view.showAxioms(axioms);
				}
			} catch (Exception e) {
				ErrorLogPanel.showErrorDialog(e);
				cancel(true);
				view.setBusyTaskEnded();
			}
			
			return null;
		}

		@Override
		public void done() {
			if(!isCancelled()){
				timer.cancel();
				if(view.getAxiomType().equals(AxiomType.EQUIVALENT_CLASSES) || view.getAxiomType().equals(AxiomType.SUBCLASS_OF)) {
					List<EvaluatedDescriptionClass> result = Manager.getInstance().getCurrentlyLearnedDescriptions();
					updateList(result);
					
				}
				view.setLearningFinished();
				setProgress(0);
			}
			ProtegeApplication.getBackgroundTaskManager().endTask(learningTask);
		}

		@Override
		protected void process(List<List<? extends EvaluatedDescription>> resultLists) {
			for (List<? extends EvaluatedDescription> list : resultLists) {
				updateList(list);
			}
		}

		private void updateList(final List<? extends EvaluatedDescription> result) {
			view.setSuggestions(result);
			view.showHorizontalExpansionMessage(Manager.getInstance().getMinimumHorizontalExpansion(),
					Manager.getInstance().getMaximumHorizontalExpansion());
		}
	}
	
}

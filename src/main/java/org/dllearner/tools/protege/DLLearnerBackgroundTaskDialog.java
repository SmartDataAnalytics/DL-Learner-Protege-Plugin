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

import org.protege.editor.core.Disposable;
import org.protege.editor.core.ui.error.ErrorLogPanel;
import org.protege.editor.core.ui.util.Resettable;

import javax.swing.*;
import java.awt.*;


/**
 * A dialog for heavy DL-Learner background tasks.
 * 
 * @author Lorenz Buehmann
 * 
 */
public class DLLearnerBackgroundTaskDialog implements Disposable, Resettable {

	public static final int PADDING = 5;

	public static final String DEFAULT_MESSAGE = "Computing...";

	private DLLearnerView view;

	private JLabel taskLabel;
	private JProgressBar progressBar = new JProgressBar();
	private JDialog dialog;

	private boolean taskIsRunning = false;

	public DLLearnerBackgroundTaskDialog(DLLearnerView v) {
		this.view = v;
	}

	public void run() {
		Manager.getInstance().setIsPreparing(true);
		view.showStatusBar(true);
		view.setBusy(true);

		SwingWorker<Void, Void> mySwingWorker = new SwingWorker<Void, Void>(){
			@Override
			protected Void doInBackground() throws Exception {

				try {
					Manager.getInstance().initKnowledgeSource();
					Manager.getInstance().initReasoner();
					if(Manager.getInstance().canLearn()){
						view.setLearningEnabled();
					} else {
						view.showNoInstancesMessage();
					}
				} catch (Exception e) {
					ErrorLogPanel.showErrorDialog(e);
				}
				return null;
			}

		};

		taskStarted("Initializing DL-Learner...");

		mySwingWorker.addPropertyChangeListener(evt -> {
            if (evt.getPropertyName().equals("state")) {
                if (evt.getNewValue() == SwingWorker.StateValue.DONE) {
                    dialog.dispose();
                    view.showStatusBar(false);
                    view.setBusy(false);
                    Manager.getInstance().setIsPreparing(false);
                }
            }
        });
		mySwingWorker.execute();
	}

	public void initDialog() {
		if(dialog != null) {
			return;
		}
		Window win = SwingUtilities.getWindowAncestor(view);
		dialog = new JDialog(win, "DL-Learner progress", Dialog.ModalityType.APPLICATION_MODAL);

		JPanel panel = new JPanel(new BorderLayout(PADDING, PADDING));
		panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
		dialog.add(panel);

		taskLabel = new JLabel(DEFAULT_MESSAGE);
		panel.add(taskLabel, BorderLayout.NORTH);

		progressBar = new JProgressBar();
		progressBar.setIndeterminate(true);
		panel.add(progressBar, BorderLayout.SOUTH);

		dialog.pack();
		dialog.setLocationRelativeTo(win);
		dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		Dimension windowSize = dialog.getPreferredSize();
		dialog.setSize(400, windowSize.height);
		dialog.setResizable(false);
	}

	public void reset() {
		SwingUtilities.invokeLater(() -> {
			initDialog();
			dialog.dispose();
		});
	}

	public void dispose() throws Exception {
		reset();
	}

	private void showWindow(final String message) {
		SwingUtilities.invokeLater(() -> {
			if (!taskIsRunning)
				return;
			initDialog();
			taskLabel.setText(message);
			if (dialog.isVisible())
				return;

			dialog.setLocationRelativeTo(dialog.getOwner());
			dialog.setVisible(true);
		});
	}

	public void taskStarted(String taskName) {
		taskStarted(taskName, false);
	}

	public void taskStarted(String taskName, boolean indeterminate) {
		if (taskIsRunning)
			return;
		taskIsRunning = true;
		SwingUtilities.invokeLater(() -> {
			progressBar.setIndeterminate(indeterminate);
			progressBar.setValue(0);
		});
		showWindow(taskName);
	}

	public void taskStopped() {
		if (!taskIsRunning)
			return;
		taskIsRunning = false;
		SwingUtilities.invokeLater(() -> {
			if (taskIsRunning)
				return;
			initDialog();
			if (!dialog.isVisible())
				return;
			taskLabel.setText("");
			dialog.setVisible(false);
		});
	}

	public void taskBusy() {
		SwingUtilities.invokeLater(() -> progressBar.setIndeterminate(true));
	}

	public void taskProgressChanged(final int value, final int max) {
		SwingUtilities.invokeLater(() -> {
			progressBar.setIndeterminate(false);
			progressBar.setMaximum(max);
			progressBar.setValue(value);
		});
	}

}

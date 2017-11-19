package org.dllearner.tools.protege;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Objects;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JSeparator;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import org.semanticweb.owlapi.reasoner.ReasonerProgressMonitor;
import org.semanticweb.owlapi.util.ProgressMonitor;

public class StatusBar extends JPanel implements PropertyChangeListener, ReasonerProgressMonitor, ProgressMonitor {

	private static final long serialVersionUID = 1L;
	private JLabel infoLabel;
	private JProgressBar progressBar;

	private boolean cancelled;

	private boolean indeterminate;

	private static final int CANCEL_TIMEOUT_MS = 5000;

	private int progress;
	private String progressTitle;

	public StatusBar() {
		setLayout(new BorderLayout());

		infoLabel = new JLabel("");
		progressBar = new JProgressBar();

		JPanel rightPanel = new JPanel(new BorderLayout());
		rightPanel.setOpaque(false);
		JPanel leftPanel = new JPanel(new FlowLayout());

		leftPanel.add(progressBar);
		leftPanel.add(new JSeparator(JSeparator.VERTICAL));
		leftPanel.add(infoLabel);
		leftPanel.add(new JSeparator(JSeparator.VERTICAL));
		leftPanel.setOpaque(false);
		add(leftPanel, BorderLayout.WEST);
//		add(rightPanel, BorderLayout.EAST);
//		setBackground(SystemColor.control);

		Timer cancelTimeout = new Timer(CANCEL_TIMEOUT_MS, event -> {

		});
		cancelTimeout.setRepeats(false);
	}

	@Override
	public boolean isCancelled() {
		return false;
	}

	public void showProgress(boolean b) {
		cancelled = false;
		indeterminate = b;
		SwingUtilities.invokeLater(() -> progressBar.setIndeterminate(indeterminate));
	}

	public void setMaximumValue(int max) {
		progressBar.setMaximum(max);
	}

	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		if (Objects.equals("progress", evt.getPropertyName())) {
			int progress = (Integer) evt.getNewValue();
			setProgress(progress);
		}
	}

	public boolean isCanceled() {
		return cancelled;
	}

	public void setProgress(int progr) {
		this.progress = progr;
		SwingUtilities.invokeLater(() -> progressBar.setValue(progress));

	}

	public void setProgressTitle(String title) {
		this.progressTitle = title;
		setMessage(progressTitle + "...");
	}

	// methods from interface ReasonerProgressMonitor

	@Override
	public void reasonerTaskBusy() {
		progressBar.setIndeterminate(true);
	}

	@Override
	public void reasonerTaskProgressChanged(int value, int max) {
		progressBar.setMaximum(max);
		setProgress(value);
	}

	@Override
	public void reasonerTaskStarted(String message) {
		setProgress(0);
		setMessage(message);
	}

	@Override
	public void reasonerTaskStopped() {
		progressBar.setIndeterminate(false);
		setMessage("");
		setProgress(0);
	}


	// methods from interface ProgressMonitor
	@Override
	public void setStarted() {

	}

	@Override
	public void setSize(long size) {

	}

	@Override
	public void setProgress(long progress) {
		this.progress = (int) progress;
		SwingUtilities.invokeLater(() -> progressBar.setValue((int) progress));


	}

	public void setMessage(String message) {
		SwingUtilities.invokeLater(() -> infoLabel.setText(message));
	}

	@Override
	public void setIndeterminate(boolean b) {
		progressBar.setIndeterminate(b);
	}

	@Override
	public void setFinished() {
		progressBar.setIndeterminate(false);
		setMessage("");
		setProgress(0);
	}


}
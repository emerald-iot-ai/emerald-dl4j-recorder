package com.matthiasrothe.emerald.dl4j.recorder.gui;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import com.matthiasrothe.emerald.dl4j.recorder.server.ServerListener;
import com.matthiasrothe.emerald.dl4j.recorder.server.Server;

public class RecorderFrame extends JFrame implements ServerListener {
	private static final long serialVersionUID = -5758677343907489214L;

	private class ExitHandler extends WindowAdapter implements ActionListener {

		@Override
		public void windowClosing(WindowEvent e) {
			exit();
		}

		@Override
		public void actionPerformed(ActionEvent e) {
			exit();
		}
		
		private void exit() {
			RecorderFrame.this.setVisible(false);
			
			try {
				if (server.isRecording()) {
					server.stopRecording();
				}
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				server.abort();
				
				try {
					Thread.sleep(2000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				
				System.exit(0);
			}
		}
	}
	
	private class RecordingButton extends JButton {
		private static final long serialVersionUID = -1508265687709239921L;
		
		private static final String START_RECORDING = "Start recording";
		private static final String STOP_RECORDING = "Stop recording";
		
		RecordingButton(final int label) {
			setText(START_RECORDING);
			setEnabled(false);
			addActionListener(event -> {
				String buttonLabel = getText();
				
				if (buttonLabel.equals(START_RECORDING) && !server.isRecording()) {
					try {
						server.startRecording(label);
						setText(STOP_RECORDING);
						disableAllButtons();
						setEnabled(true);
					} catch (IOException e) {
						e.printStackTrace();
					}
				} else if (buttonLabel.equals(STOP_RECORDING) && server.isRecording()) {
					try {
						server.stopRecording();
						setText(START_RECORDING);
						enableAllButtons();
						dataPointsWritten = 0;
					} catch (IOException e) {
						e.printStackTrace();
					}
				} else {
					JOptionPane.showMessageDialog(RecorderFrame.this, "Illegal State: Button and Server don't match!");
				}
			});
		}
	}
	
	private final Server server;
	private final List<RecordingButton> buttons = new ArrayList<>();
	private final JLabel statusLabel = new JLabel("Server started. Waiting for client.");
	
	private int dataPointsWritten = 0;
	
	public RecorderFrame(final Server server) {
		super("Emerald DL4J Recorder");
		
		Objects.requireNonNull(server, "server must not be null");
		this.server = server;
		
		for (int i = 0; i < 10; i++) {
			buttons.add(new RecordingButton(i));
		}
		
		setContentPane(getContentPanel());
	}

	private JPanel getContentPanel() {
		JPanel contentPanel = new JPanel();
		
		contentPanel.setLayout(new GridBagLayout());
		
		String[] labels = new String[] {
				"Label: 0 (Rest)",
				"Label: 1 (Pick Up)",
				"Label: 2 (Put Down)",
				"Label: 3 (Shake)",
				"Label: 4 (Turn Forward)",
				"Label: 5 (Turn Backward)",
				"Label: 6 (Turn Right)",
				"Label: 7 (Turn Left)",
				"Label: 8 (Turn Clockwise)",
				"Label: 9 (Turn Counter-Clockwise)"
		};
		
		for (int i = 0; i < 10; i++) {
			GridBagConstraints labelConstraints = new GridBagConstraints();
			labelConstraints.gridx = 0;
			labelConstraints.gridy = i;
			labelConstraints.anchor = GridBagConstraints.WEST;
			labelConstraints.insets = new Insets(5, 5, 5, 5);
					
			contentPanel.add(new JLabel(labels[i]), labelConstraints);
		}
		
		for (int i = 0; i < 10; i++) {
			GridBagConstraints buttonConstraints = new GridBagConstraints();
			buttonConstraints.gridx = 1;
			buttonConstraints.gridy = i;
			buttonConstraints.insets = new Insets(5, 0, 5, 5);
			
			contentPanel.add(buttons.get(i), buttonConstraints);
		}
		
		GridBagConstraints statusLabelConstraints = new GridBagConstraints();
		statusLabelConstraints.gridx = 0;
		statusLabelConstraints.gridy = 10;
		statusLabelConstraints.gridwidth = 2;
		statusLabelConstraints.insets = new Insets(5, 5, 5, 5);
		
		contentPanel.add(statusLabel, statusLabelConstraints);
		
		return contentPanel;
	}
	
	public void init() {
		// set generic properties
		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
		setResizable(false);
		addWindowListener(new ExitHandler());
		
		// Position application window in the middle of the screen
		pack();
		Dimension screenSize = this.getToolkit().getScreenSize();
		Point location = new Point();
		location.x = (int) ((screenSize.getWidth() / 2) - (getWidth() / 2));
		location.y = (int) ((screenSize.getHeight() / 2) - (getHeight() / 2));
		this.setLocation(location);

		// Set window visible
		setVisible(true);
	}

	@Override
	public void clientConnected() {
		SwingUtilities.invokeLater(() -> {
			statusLabel.setText("Client connected. Happy recording!");
			enableAllButtons();
		});
	}
	
	private void enableAllButtons() {
		for (RecordingButton button : buttons) {
			button.setEnabled(true);
		}
	}

	@Override
	public void clientDisconnected() {
		SwingUtilities.invokeLater(() -> {
			statusLabel.setText("Client disconnected.");
			disableAllButtons();
		});
	}
	
	private void disableAllButtons() {
		for (RecordingButton button : buttons) {
			button.setEnabled(false);
		}
	}
	
	@Override
	public void dataPointWritten() {
		SwingUtilities.invokeLater(() -> {
			dataPointsWritten++;
			statusLabel.setText("Recording in progress. " + dataPointsWritten + " data points written.");
		});
	}
}

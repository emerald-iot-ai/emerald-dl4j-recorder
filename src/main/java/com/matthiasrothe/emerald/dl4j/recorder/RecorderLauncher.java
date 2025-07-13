package com.matthiasrothe.emerald.dl4j.recorder;

import java.io.IOException;

import javax.swing.SwingUtilities;

import com.matthiasrothe.emerald.dl4j.recorder.gui.RecorderFrame;
import com.matthiasrothe.emerald.dl4j.recorder.server.Server;

public class RecorderLauncher {
	public static void main(String[] args) throws IOException {
		Server server = new Server(5000, "./recorded-data");
		RecorderFrame frame = new RecorderFrame(server);
		
		server.setClientConnectionListener(frame);
		
		SwingUtilities.invokeLater(() -> frame.init());
		server.start();
	}
}

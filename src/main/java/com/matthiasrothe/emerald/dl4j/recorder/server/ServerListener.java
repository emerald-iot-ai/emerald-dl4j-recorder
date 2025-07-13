package com.matthiasrothe.emerald.dl4j.recorder.server;

public interface ServerListener {
	void clientConnected();
	void clientDisconnected();
	void dataPointWritten();
}

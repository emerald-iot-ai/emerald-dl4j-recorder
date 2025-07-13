package com.matthiasrothe.emerald.dl4j.recorder.server;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.UUID;

import net.sf.jetro.stream.visitor.LazilyParsedNumber;
import net.sf.jetro.tree.JsonArray;
import net.sf.jetro.tree.JsonNumber;
import net.sf.jetro.tree.JsonObject;
import net.sf.jetro.tree.JsonProperty;
import net.sf.jetro.tree.JsonString;
import net.sf.jetro.tree.JsonType;
import net.sf.jetro.tree.builder.JsonTreeBuilder;

public class Server extends Thread {
	private static final int STATUS_CODE_OK = 200;
	private static final int STATUS_CODE_INVALID_JSON = 400;
	private static final int STATUS_CODE_MALFORMED_MESSAGE = 401;
	private static final int STATUS_CODE_HEADER_MISSING = 402;
	private static final int STATUS_CODE_UNSUPPORTED_MESSAGE_TYPE = 403;
	private static final int STATUS_CODE_BODY_MISSING = 404;
	
	private final int port;
	private final String dataDirectory;
	
	private boolean abort;
	private final Object abortMutex = new Object();
	
	private final File configFile = Paths.get("./config/config.json").toFile();
	
	private int nextFileNumber;
	private BufferedWriter dataWriter;
	private final Object recordingMutex = new Object();
	
	private final JsonTreeBuilder builder = new JsonTreeBuilder();

	private ServerListener listener;
	
	public Server(final int port, final String dataDirectory) throws IOException {
		Objects.requireNonNull(dataDirectory, "dataDirectory must not be null");
		
		this.port = port;
		this.dataDirectory = dataDirectory;
		
		Paths.get(dataDirectory).toFile().mkdir();
		
		try (BufferedReader configReader = new BufferedReader(new FileReader(configFile))) {			
			JsonObject config = (JsonObject) builder.build(configReader);
			nextFileNumber = ((LazilyParsedNumber)((JsonNumber) config.get("nextFileNumber")).getValue()).intValue();
		}
	}

	public void setClientConnectionListener(final ServerListener listener) {
		this.listener = listener;
	}
	
	public void abort() {
		synchronized (abortMutex) {
			abort = true;
		}
	}
	
	private boolean shouldAbort() {
		synchronized (abortMutex) {
			return abort;
		}
	}
	
	public void startRecording(final int label) throws IOException {
		if (!isRecording()) {
			synchronized (recordingMutex) {
				try (BufferedWriter labelWriter = new BufferedWriter(new FileWriter(
						Paths.get(dataDirectory + "/label_" + nextFileNumber + ".csv").toFile()))) {
					labelWriter.write(label + "");
				}
				
				dataWriter = new BufferedWriter(new FileWriter(
						Paths.get(dataDirectory + "/data_" + nextFileNumber + ".csv").toFile()));
				
				nextFileNumber++;
				writeConfig();
			}
		} else {
			throw new IllegalStateException("Recording already in progress");
		}
	}

	public boolean isRecording() {
		synchronized (recordingMutex) {
			return dataWriter != null;
		}
	}
	
	public void stopRecording() throws IOException {
		if (isRecording()) {
			synchronized (recordingMutex) {
				dataWriter.close();
				dataWriter = null;
			}
		} else {
			throw new IllegalStateException("Recording is not in progress");
		}
	}
	
	@Override
	public void run() {
		try (ServerSocket serverSocket = new ServerSocket(port)) {
			System.out.println("Server listening on port " + port + "...");
			
			while (!shouldAbort()) {
				try {
					Socket clientSocket = serverSocket.accept();
					System.out.println("Client connected: " + clientSocket.getInetAddress());
					
					if (listener != null) {
						listener.clientConnected();
					}
					
					BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
					BufferedWriter out = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
					
					String line;
					
					while ((line = in.readLine()) != null) {
						JsonObject message = null;
						
						try {
							message = (JsonObject) builder.build(line);
						} catch (Exception e) {
							respondInvalidJson(line, out);
							break;
						}
						
						boolean ok = processMessage(message, out);
						
						if (ok) {
							respondOk((JsonString) ((JsonObject) message.get("header")).get("uuid"), out);
						}
					}
					
					clientSocket.close();
					
					if (listener != null) {
						listener.clientDisconnected();
					}
				} catch (SocketException e) {
					if (listener != null) {
						listener.clientDisconnected();
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private boolean processMessage(final JsonObject message, final BufferedWriter out) throws IOException {
		try {
			JsonObject header = (JsonObject) message.get("header");
			
			if (header != null) {
				String type = ((JsonString) header.get("type")).getValue();
				JsonString uuid = (JsonString) header.get("uuid");
				
				if (uuid == null) {
					throw new IllegalArgumentException();
				} else {
					UUID.fromString(uuid.getValue()); // to throw IllegalArgumentException if the value is no UUID
				}
				
				switch (type) {
					case "single-value":
						return processSingleValueMessage(message, uuid, out);
					case "batch":
						return processBatchMessage(message, uuid, out);
					default:
						respond("Bad Request: Unsupported message type [" + type + "].",
								STATUS_CODE_UNSUPPORTED_MESSAGE_TYPE, uuid, out);
				}
			} else {
				respond("Bad Request: Header missing.", STATUS_CODE_HEADER_MISSING, null, out);
			}
		} catch (Exception e) {
			if (e instanceof IOException) {
				throw e;
			}
			
			respond("Bad Request: Malformed message.", STATUS_CODE_MALFORMED_MESSAGE, null, out);
		}
		
		return false;
	}
	
	private boolean processSingleValueMessage(JsonObject message, JsonString uuid, BufferedWriter out)
			throws IOException {
		try {
			JsonObject body = (JsonObject) message.get("body");
			
			if (body == null) {
				respond("Bad Request: Body Missing.", STATUS_CODE_BODY_MISSING, uuid, out);
				return false;
			}
			
			processSingleValue(body);
			return true;
		} catch (Exception e) {
			if (e instanceof IOException) {
				throw (IOException) e;
			}
			
			respond("Bad Request: Malformed message.", STATUS_CODE_MALFORMED_MESSAGE, uuid, out);			
		}
		
		return false;
	}

	private boolean processBatchMessage(JsonObject message, JsonString uuid, BufferedWriter out) throws IOException {
		try {
			JsonArray body = (JsonArray) message.get("body");
			
			if (body == null) {
				respond("Bad Request: Body Missing.", STATUS_CODE_BODY_MISSING, uuid, out);
				return false;
			}

			for (JsonType value : body) {
				processSingleValue((JsonObject) value);
			}
			
			return true;
		} catch (Exception e) {
			if (e instanceof IOException) {
				throw (IOException) e;
			}
			
			respond("Bad Request: Malformed message.", STATUS_CODE_MALFORMED_MESSAGE, uuid, out);			
		}
		
		return false;
	}

	private void processSingleValue(final JsonObject value) throws Exception {
		if (isRecording()) {
			JsonArray values = (JsonArray) value.get("values");
			double x = ((LazilyParsedNumber)((JsonNumber) values.get(0)).getValue()).doubleValue();
			double y = ((LazilyParsedNumber)((JsonNumber) values.get(1)).getValue()).doubleValue();
			double z = ((LazilyParsedNumber)((JsonNumber) values.get(2)).getValue()).doubleValue();
			
			String data = x + "," + y + "," + z + "\n";
			
			synchronized (recordingMutex) {
				dataWriter.write(data);
			}
			
			if (listener != null) {
				listener.dataPointWritten();
			}
		}
	}
	
	private void respondOk(final JsonString uuid, final BufferedWriter out) throws IOException {
		respond("OK", STATUS_CODE_OK, uuid, out);
	}
	
	private void respondInvalidJson(final String line, final BufferedWriter out) throws IOException {
		respond("Bad Request: Invalid JSON: [" + line +	"]. Connection will be closed.",
				STATUS_CODE_INVALID_JSON, null, out);
	}
	
	private void respond(final String status, final int statusCode, final JsonString uuid, final BufferedWriter out)
			throws IOException {
		JsonObject response = new JsonObject();
		response.add(new JsonProperty("status", status));
		response.add(new JsonProperty("statusCode", statusCode));

		if (uuid != null) {
			response.add(new JsonProperty("uuid", uuid));
		}
		
		out.write(response.toJson());
		out.newLine();
		out.flush();
	}
	
	private void writeConfig() throws IOException {
		JsonObject config = new JsonObject();
		config.add(new JsonProperty("nextFileNumber", nextFileNumber));
		
		try (BufferedWriter configWriter = new BufferedWriter(new FileWriter(configFile))) {
			configWriter.write(config.toJson());
		}
	}
}

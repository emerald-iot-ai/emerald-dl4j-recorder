package com.matthiasrothe.emerald.dl4j.recorder.server;

import static org.testng.Assert.assertEquals;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.UUID;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import net.sf.jetro.tree.JsonArray;
import net.sf.jetro.tree.JsonNumber;
import net.sf.jetro.tree.JsonObject;
import net.sf.jetro.tree.JsonProperty;
import net.sf.jetro.tree.builder.JsonTreeBuilder;

public class ServerTest {
	private static final File CONFIG_FILE = Paths.get("./config/config.json").toFile();
	private static final JsonTreeBuilder BUILDER = new JsonTreeBuilder();
	private static final String DATA_DIRECTORY = "./test-recorded-data";
	
	private Server server;
	private Socket clientSocket;
	private BufferedReader in;
	private BufferedWriter out;
	private JsonObject config;
	
	@BeforeClass
	public void setup() throws Exception {
		try (BufferedReader configReader = new BufferedReader(new FileReader(CONFIG_FILE))) {
			config = (JsonObject) BUILDER.build(configReader);
		}
		
		server = new Server(5001, DATA_DIRECTORY);
		server.start();
		
		Thread.sleep(2000);
		
		clientSocket = new Socket("localhost", 5001);
		in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
		out = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
	}
	
	@Test
	public void shouldReceiveSingleValueBody() throws Exception {
		server.startRecording(0);

		String uuid = UUID.randomUUID().toString();
		
		JsonObject header = new JsonObject();
		header.add(new JsonProperty("type", "single-value"));
		header.add(new JsonProperty("uuid", uuid));
		
		JsonObject body = new JsonObject();
		body.add(new JsonProperty("sensor", "testdata"));
		body.add(new JsonProperty("timestamp", LocalDateTime.now().toString()));
		body.add(new JsonProperty("values", new JsonArray(Arrays.asList(
				new JsonNumber(0.12345), new JsonNumber(2.6789012), new JsonNumber(9.3456789)))));
		
		JsonObject message = new JsonObject();
		message.add(new JsonProperty("header", header));
		message.add(new JsonProperty("body", body));
		
		out.write(message.toJson());
		out.newLine();
		out.flush();
		
		String response = in.readLine();
		System.out.println("Response: " + response);
		
		server.stopRecording();
		
		assertEquals(response, "{\"status\":\"OK\",\"statusCode\":200,\"uuid\":\"" + uuid + "\"}");
	}
	
	@Test(dependsOnMethods = "shouldReceiveSingleValueBody")
	public void shouldReceiveBatchBody() throws Exception {
		server.startRecording(1);

		String uuid = UUID.randomUUID().toString();

		JsonObject header = new JsonObject();
		header.add(new JsonProperty("type", "batch"));
		header.add(new JsonProperty("uuid", uuid));
		
		JsonObject value1 = new JsonObject();
		value1.add(new JsonProperty("sensor", "testdata"));
		value1.add(new JsonProperty("timestamp", LocalDateTime.now().toString()));
		value1.add(new JsonProperty("values", new JsonArray(Arrays.asList(
				new JsonNumber(0.12345), new JsonNumber(2.6789012), new JsonNumber(9.3456789)))));
		
		JsonObject value2 = new JsonObject();
		value2.add(new JsonProperty("sensor", "testdata"));
		value2.add(new JsonProperty("timestamp", LocalDateTime.now().toString()));
		value2.add(new JsonProperty("values", new JsonArray(Arrays.asList(
				new JsonNumber(5.432109), new JsonNumber(2.6789012), new JsonNumber(9.3456789)))));
		
		JsonObject value3 = new JsonObject();
		value3.add(new JsonProperty("sensor", "testdata"));
		value3.add(new JsonProperty("timestamp", LocalDateTime.now().toString()));
		value3.add(new JsonProperty("values", new JsonArray(Arrays.asList(
				new JsonNumber(9.12345), new JsonNumber(2.6789012), new JsonNumber(9.3456789)))));
		
		JsonArray body = new JsonArray(Arrays.asList(value1, value2, value3));
		
		JsonObject message = new JsonObject();
		message.add(new JsonProperty("header", header));
		message.add(new JsonProperty("body", body));
		
		out.write(message.toJson());
		out.newLine();
		out.flush();
		
		String response = in.readLine();
		System.out.println("Response: " + response);
		
		server.stopRecording();

		assertEquals(response, "{\"status\":\"OK\",\"statusCode\":200,\"uuid\":\"" + uuid + "\"}");
	}
	
	@AfterClass
	public void teardown() throws Exception {
		clientSocket.close();		
		server.abort();
		
		File dataDirectory = Paths.get(DATA_DIRECTORY).toFile();
		for (String fileName : dataDirectory.list()) {
			Paths.get(DATA_DIRECTORY + "/" + fileName).toFile().delete();
		}
		dataDirectory.delete();
		
		try (BufferedWriter configWriter = new BufferedWriter(new FileWriter(CONFIG_FILE))) {
			configWriter.write(config.toJson());
		}
	}
}

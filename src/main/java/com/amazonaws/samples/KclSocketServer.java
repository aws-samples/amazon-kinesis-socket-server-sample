/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this
 * software and associated documentation files (the "Software"), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify,
 * merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

 package com.amazonaws.samples;

import java.io.IOException;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.UUID;

import com.amazonaws.services.kinesis.clientlibrary.interfaces.v2.IRecordProcessorFactory;
import com.amazonaws.services.kinesis.clientlibrary.lib.worker.InitialPositionInStream;
import com.amazonaws.services.kinesis.clientlibrary.lib.worker.KinesisClientLibConfiguration;
import com.amazonaws.services.kinesis.clientlibrary.lib.worker.Worker;

public class KclSocketsServer implements IRecordProcessorListener {

	private KinesisClientLibConfiguration kinesisClientLibConfiguration;
	private Worker worker;
	private ServerSocket _socket;
	private Socket _conn;


	public KclSocketsServer(String appName, String streamName, InitialPositionInStream initialPos, int SocketPort) {

		// Ensure the JVM will refresh the cached IP values of AWS resources (e.g.
		// service endpoints).
		java.security.Security.setProperty("networkaddress.cache.ttl", "60");

		StringBuilder workerId = new StringBuilder();
		try {
			workerId.append(InetAddress.getLocalHost().getCanonicalHostName());
			_socket = new ServerSocket(SocketPort, 0, InetAddress.getLoopbackAddress()) ;
			System.out.println("Waiting for socket connnection");
			_conn = _socket.accept();
			System.out.println("Connection Recieved");
		}
		catch (UnknownHostException e) {
			System.out.println("WARNING: Unable to get system name");
		}
		catch(IOException e) {
			System.out.println(String.format("ERROR: unable to start socket server.\n\rError detail: %s", e.getMessage()));
			return;
		}
		if (workerId.length() > 0) {
			workerId.append(":");
		}
		workerId.append(UUID.randomUUID());

		kinesisClientLibConfiguration = new KinesisClientLibConfiguration(appName, streamName,
				Utils.GetCredentialProvider(), workerId.toString());
		kinesisClientLibConfiguration.withInitialPositionInStream(initialPos);

		IRecordProcessorFactory recordProcessorFactory = new KclRecordProcessorFactory(this);
		// Worker worker = new Worker(recordProcessorFactory,
		// kinesisClientLibConfiguration);
		worker = new Worker.Builder().recordProcessorFactory(recordProcessorFactory)
				.config(kinesisClientLibConfiguration).build();

		System.out.printf("Creating %s as worker %s to process stream %s\n",
				kinesisClientLibConfiguration.getApplicationName(), kinesisClientLibConfiguration.getWorkerIdentifier(),
				kinesisClientLibConfiguration.getStreamName());
	}

	public void Run() {
		System.out.printf("Running %s...\n", kinesisClientLibConfiguration.getApplicationName());
		int exitCode = 0;
		try {
			worker.run();
		} catch (Throwable t) {
			System.err.println("Caught throwable while processing data.");
			t.printStackTrace();
			exitCode = 1;
		}
		System.exit(exitCode);
	}

	public void Notify(String partitionKey, String sequenceNumber, String dataBlob) {
		try {
			PrintStream out = new PrintStream(_conn.getOutputStream());
			out.println(String.format("{partitionKey: \"%s\", sequenceNumber: \"%s\", dataBlob: %s}", partitionKey, sequenceNumber, dataBlob));
	        out.flush();
		}
		catch(IOException e) {
			System.out.println("ERROR: unable to write to socket.\r\n" + e.getMessage());
		}
	}
}

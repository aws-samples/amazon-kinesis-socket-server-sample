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

import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.kinesis.AmazonKinesis;
import com.amazonaws.services.kinesis.AmazonKinesisClientBuilder;
import com.amazonaws.services.kinesis.clientlibrary.lib.worker.InitialPositionInStream;
import com.amazonaws.services.kinesis.model.ResourceNotFoundException;

public class ServerBootstrap {

	public static final String SAMPLE_APPLICATION_STREAM_NAME = "<STREAM NAME HERE>"; //change this

	private static final String SAMPLE_APPLICATION_NAME = "<NAME OF APP HERE>"; //change this

	private static int SOCKET_PORT = 8099; //this much match the client

	public static void main(String[] args) {
		KclSocketServer server = new KclSocketServer(SAMPLE_APPLICATION_NAME, SAMPLE_APPLICATION_STREAM_NAME,
				InitialPositionInStream.TRIM_HORIZON, SOCKET_PORT);

		if (args.length == 1 && "delete-resources".equals(args[0])) {
			deleteResources();
			return;
		}
		server.Run();
	}

	public static void deleteResources() {

		ProfileCredentialsProvider credentialsProvider = Utils.GetCredentialProvider();
		// Delete the stream
		AmazonKinesis kinesis = AmazonKinesisClientBuilder.standard().withCredentials(credentialsProvider)
				.withRegion("us-east-1").build();

		System.out.printf("Deleting the Amazon Kinesis stream used by the sample. Stream Name = %s.\n",
				SAMPLE_APPLICATION_STREAM_NAME);
		try {
			kinesis.deleteStream(SAMPLE_APPLICATION_STREAM_NAME);
		} catch (ResourceNotFoundException ex) {
			// The stream doesn't exist.
		}

		// Delete the table
		AmazonDynamoDB dynamoDB = AmazonDynamoDBClientBuilder.standard().withCredentials(credentialsProvider)
				.withRegion("us-east-1").build();
		System.out.printf(
				"Deleting the Amazon DynamoDB table used by the Amazon Kinesis Client Library. Table Name = %s.\n",
				SAMPLE_APPLICATION_NAME);
		try {
			dynamoDB.deleteTable(SAMPLE_APPLICATION_NAME);
		} catch (com.amazonaws.services.dynamodbv2.model.ResourceNotFoundException ex) {
			// The table doesn't exist.
		}
	}

}

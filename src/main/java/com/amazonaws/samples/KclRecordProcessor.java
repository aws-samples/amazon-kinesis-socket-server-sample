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

import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.amazonaws.services.kinesis.clientlibrary.exceptions.InvalidStateException;
import com.amazonaws.services.kinesis.clientlibrary.exceptions.ShutdownException;
import com.amazonaws.services.kinesis.clientlibrary.exceptions.ThrottlingException;
import com.amazonaws.services.kinesis.clientlibrary.interfaces.IRecordProcessorCheckpointer;
import com.amazonaws.services.kinesis.clientlibrary.interfaces.v2.IRecordProcessor;
import com.amazonaws.services.kinesis.clientlibrary.lib.worker.ShutdownReason;
import com.amazonaws.services.kinesis.clientlibrary.types.InitializationInput;
import com.amazonaws.services.kinesis.clientlibrary.types.ProcessRecordsInput;
import com.amazonaws.services.kinesis.clientlibrary.types.ShutdownInput;
import com.amazonaws.services.kinesis.model.Record;

public class KclRecordProcessor implements IRecordProcessor
{
	private static final Log LOG = LogFactory.getLog(KclRecordProcessor.class);
    private String kinesisShardId;

    // Backoff and retry settings
    private static final long BACKOFF_TIME_IN_MILLIS = 3000L;
    private static final int NUM_RETRIES = 10;

    // Checkpoint about once a minute
    private static final long CHECKPOINT_INTERVAL_MILLIS = 60000L;
    private long nextCheckpointTimeInMillis;

    private final CharsetDecoder decoder = Charset.forName("UTF-8").newDecoder();

    private IRecordProcessorListener rpListener = null;

    public KclRecordProcessor(IRecordProcessorListener listener) {
		rpListener = listener;
	}

	public void initialize(InitializationInput input) {
		LOG.info("Initializing record processor for shard: " + input.getShardId());
        this.kinesisShardId = input.getShardId();

	}

	public void processRecords(ProcessRecordsInput input) {
		List<Record> records = input.getRecords();
		LOG.info("Processing " + records.size() + " records from " + kinesisShardId);

		// Process records and perform all exception handling.
        processRecordsWithRetries(records);

        // Checkpoint once every checkpoint interval.
        if (System.currentTimeMillis() > nextCheckpointTimeInMillis) {
            checkpoint(input.getCheckpointer());
            nextCheckpointTimeInMillis = System.currentTimeMillis() + CHECKPOINT_INTERVAL_MILLIS;
        }

	}

	public void shutdown(ShutdownInput input) {
		 LOG.info("Shutting down record processor for shard: " + kinesisShardId);
	        // Important to checkpoint after reaching end of shard, so we can start processing data from child shards.
	        if (input.getShutdownReason() == ShutdownReason.TERMINATE) {
	            checkpoint(input.getCheckpointer());
	}

	}

    /**
     * Process records performing retries as needed. Skip "poison pill" records.
     *
     * @param records Data records to be processed.
     */
    private void processRecordsWithRetries(List<Record> records) {
        for (Record record : records) {
            boolean processedSuccessfully = false;
            for (int i = 0; i < NUM_RETRIES; i++) {
                try {
                    //
                    // Logic to process record goes here.
                    //
                    processSingleRecord(record);

                    processedSuccessfully = true;
                    break;
                } catch (Throwable t) {
                    LOG.warn("Caught throwable while processing record " + record, t);
                }

                // backoff if we encounter an exception.
                try {
                    Thread.sleep(BACKOFF_TIME_IN_MILLIS);
                } catch (InterruptedException e) {
                    LOG.debug("Interrupted sleep", e);
                }
            }

            if (!processedSuccessfully) {
                LOG.error("Couldn't process record " + record + ". Skipping the record.");
            }
        }
    }


    /**
     * Process a single record.
     *
     * @param record The record to be processed.
     */
    private void processSingleRecord(Record record) {
        // TODO Add your own record processing logic here

        String data = null;
        try {
            // For this app, we interpret the payload as UTF-8 chars.
            data = decoder.decode(record.getData()).toString();

            /* original sample code
            // Assume this record came from AmazonKinesisSample and log its age.
            long recordCreateTime = new Long(data.substring("testData-".length()));
            long ageOfRecordInMillis = System.currentTimeMillis() - recordCreateTime;
            LOG.info(record.getSequenceNumber() + ", " + record.getPartitionKey() + ", " + data + ", Created "
                    + ageOfRecordInMillis + " milliseconds ago.");
                    */
            rpListener.Notify(record.getPartitionKey(), record.getSequenceNumber(), data);
            LOG.info("Sent record to server"
            		+ "\r\n--Sequence Number: " + record.getSequenceNumber() + "\r\n"
        			+ "--Partition Key: " + record.getPartitionKey() + "\r\n"
        			+ "--Data blob: " + data);

        }
        catch (CharacterCodingException e) {
            LOG.error("Malformed data: " + data, e);
        }
    }

    /** Checkpoint with retries.
     * @param checkpointer
     */
    private void checkpoint(IRecordProcessorCheckpointer checkpointer) {
        LOG.info("Checkpointing shard " + kinesisShardId);
        for (int i = 0; i < NUM_RETRIES; i++) {
            try {
                checkpointer.checkpoint();
                break;
            } catch (ShutdownException se) {
                // Ignore checkpoint if the processor instance has been shutdown (fail over).
                LOG.info("Caught shutdown exception, skipping checkpoint.", se);
                break;
            } catch (ThrottlingException e) {
                // Backoff and re-attempt checkpoint upon transient failures
                if (i >= (NUM_RETRIES - 1)) {
                    LOG.error("Checkpoint failed after " + (i + 1) + "attempts.", e);
                    break;
                } else {
                    LOG.info("Transient issue when checkpointing - attempt " + (i + 1) + " of "
                            + NUM_RETRIES, e);
                }
            } catch (InvalidStateException e) {
                // This indicates an issue with the DynamoDB table (check for table, provisioned IOPS).
                LOG.error("Cannot save checkpoint to the DynamoDB table used by the Amazon Kinesis Client Library.", e);
                break;
            }
            try {
                Thread.sleep(BACKOFF_TIME_IN_MILLIS);
            } catch (InterruptedException e) {
                LOG.debug("Interrupted sleep", e);
            }
        }
    }
}
